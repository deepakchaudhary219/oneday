package oneday.e2ee;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import oneday.common.ApiException;
import oneday.common.RateLimiter;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.identity.UserGuard;
import oneday.realtime.RealtimeService;
import oneday.safety.BlockChecker;
import oneday.security.SessionLiveness;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server half of end-to-end encrypted Friend Mode chat (Signal-style: X3DH to start a session, a double
 * ratchet after). The server is a key directory and a ciphertext relay:
 *
 * <ul>
 * <li><b>Devices.</b> Each install registers an identity key, a signed prekey and a batch of one-time prekeys.
 * A device is bound to the sign-in session that registered it, so ending that session (sign-out, "sign out
 * that phone", refresh-token theft detection) unlinks it.</li>
 * <li><b>Bundles.</b> Fetched per connection (never by user id), each one-time prekey handed out at most
 * once, with a per-pair rate limit so prekeys can't be drained.</li>
 * <li><b>Delivery.</b> One envelope per recipient device and per sender's other device. The sender must
 * cover exactly the current device set, otherwise 409 names what's missing or stale (so a device the sender
 * doesn't know about can't silently miss messages).</li>
 * <li><b>Inbox.</b> Envelopes wait per device until acknowledged, at most 30 days.</li>
 * </ul>
 */
@Service
public class E2eeService {

	static final int MAX_DEVICES = 5;

	static final int MAX_PREKEYS_STORED = 200;

	static final int MAX_PREKEYS_PER_UPLOAD = 100;

	static final int MAX_CIPHERTEXT = 12_288;

	static final Duration ENVELOPE_RETENTION = Duration.ofDays(30);

	private final E2eeDeviceRepository devices;

	private final OneTimePreKeyRepository preKeys;

	private final EnvelopeRepository envelopes;

	private final ConnectionService connections;

	private final BlockChecker blocks;

	private final UserGuard guard;

	private final SessionLiveness liveness;

	private final RealtimeService realtime;

	private final RateLimiter rateLimiter;

	private final Clock clock;

	public E2eeService(E2eeDeviceRepository devices, OneTimePreKeyRepository preKeys, EnvelopeRepository envelopes,
			ConnectionService connections, BlockChecker blocks, UserGuard guard, SessionLiveness liveness,
			RealtimeService realtime, RateLimiter rateLimiter, Clock clock) {
		this.devices = devices;
		this.preKeys = preKeys;
		this.envelopes = envelopes;
		this.connections = connections;
		this.blocks = blocks;
		this.guard = guard;
		this.liveness = liveness;
		this.realtime = realtime;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	// ---- devices ---------------------------------------------------------------------------------------

	@Transactional
	public DeviceView register(String userId, String sessionId, NewDevice request) {
		guard.requireContactAllowed(userId);
		List<E2eeDevice> linked = linked(userId);
		Optional<E2eeDevice> existing = linked.stream().filter(d -> d.getSessionId().equals(sessionId)).findFirst();
		if (existing.isPresent()) {
			throw ApiException.conflict("DEVICE_EXISTS", "This sign-in already has secure chat set up")
				.with("deviceId", existing.get().deviceId());
		}
		if (linked.size() >= MAX_DEVICES) {
			throw ApiException.conflict("TOO_MANY_DEVICES", "Unlink a device to add this one");
		}
		if (request.registrationId() < 1 || request.registrationId() > 16380) {
			throw ApiException.badRequest("INVALID_REGISTRATION_ID", "registrationId is 1-16380");
		}
		Instant now = clock.instant();
		E2eeDevice device = new E2eeDevice(new E2eeDevice.Key(userId, devices.maxDeviceId(userId) + 1), sessionId,
				request.registrationId(), key("identityKey", request.identityKey()), now);
		SignedPreKey spk = requireSignedPreKey(request.signedPreKey());
		device.rotateSignedPreKey(spk.keyId(), key("signedPreKey", spk.publicKey()),
				bytes("signature", spk.signature(), 64, 128), now);
		devices.save(device);
		addPreKeys(device, request.oneTimePreKeys());
		return view(device);
	}

	@Transactional
	public DeviceView rotateSignedPreKey(String userId, String sessionId, int deviceId, SignedPreKey request) {
		E2eeDevice device = requireOwn(userId, sessionId, deviceId);
		SignedPreKey spk = requireSignedPreKey(request);
		device.rotateSignedPreKey(spk.keyId(), key("signedPreKey", spk.publicKey()),
				bytes("signature", spk.signature(), 64, 128), clock.instant());
		return view(device);
	}

	@Transactional
	public DeviceView uploadPreKeys(String userId, String sessionId, int deviceId, List<PreKey> batch) {
		E2eeDevice device = requireOwn(userId, sessionId, deviceId);
		addPreKeys(device, batch);
		return view(device);
	}

	@Transactional
	public List<DeviceView> mine(String userId) {
		guard.requireExisting(userId);
		return linked(userId).stream().map(this::view).toList();
	}

	@Transactional
	public void unlink(String userId, int deviceId) {
		guard.requireExisting(userId);
		devices.findById(new E2eeDevice.Key(userId, deviceId))
			.orElseThrow(() -> ApiException.notFound("Device"));
		remove(new E2eeDevice.Key(userId, deviceId));
	}

	// ---- bundles -----------------------------------------------------------------------------------------

	/** The other person's devices, each with one one-time prekey if any are left. */
	@Transactional
	public List<Bundle> bundlesFor(String userId, String connectionId) {
		guard.requireContactAllowed(userId);
		Connection connection = connections.requireMember(connectionId, userId);
		String other = connection.otherThan(userId);
		if (!connection.isActive() || blocks.isBlockedEitherWay(userId, other)) {
			throw ApiException.conflict("CONNECTION_INACTIVE", "This connection is no longer active");
		}
		if (!rateLimiter.tryAcquire("e2ee-bundles:" + userId + ":" + connectionId, 60, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("BUNDLE_RATE_LIMIT", "Too many key requests. Try again later.");
		}
		return linked(other).stream().map(this::bundle).toList();
	}

	/** The caller's other devices, so a message reaches them too. */
	@Transactional
	public List<Bundle> ownBundles(String userId, String sessionId, int deviceId) {
		requireOwn(userId, sessionId, deviceId);
		if (!rateLimiter.tryAcquire("e2ee-bundles:" + userId + ":self", 60, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("BUNDLE_RATE_LIMIT", "Too many key requests. Try again later.");
		}
		return linked(userId).stream().filter(d -> d.deviceId() != deviceId).map(this::bundle).toList();
	}

	// ---- delivery (called by chat) ---------------------------------------------------------------------

	/**
	 * Checks the sender's device and that the envelopes cover exactly the current devices on both sides, then
	 * queues them and pings both people's open apps. Runs inside the caller's transaction.
	 */
	@Transactional
	public void deliver(String senderId, String sessionId, int senderDevice, String recipientId, String conversationId,
			String messageId, List<Outgoing> outgoing) {
		requireOwn(senderId, sessionId, senderDevice);
		Set<Integer> theirs = ids(linked(recipientId));
		if (theirs.isEmpty()) {
			throw ApiException.conflict("E2EE_UNAVAILABLE", "They haven't set up secure chat on any device yet");
		}
		if (outgoing.isEmpty() || outgoing.size() > 2 * MAX_DEVICES) {
			throw ApiException.badRequest("INVALID_ENVELOPES", "One envelope per device");
		}
		Set<Integer> mine = ids(linked(senderId));
		mine.remove(senderDevice);
		Set<Integer> sentThem = targets(outgoing, false);
		Set<Integer> sentMe = targets(outgoing, true);
		long distinct = outgoing.stream().map(o -> o.toSelf() + ":" + o.deviceId()).distinct().count();
		if (!theirs.equals(sentThem) || !mine.equals(sentMe) || distinct != outgoing.size()) {
			throw ApiException.conflict("DEVICE_LIST_MISMATCH", "Devices changed. Refresh keys and resend.")
				.with("missing", Map.of("them", minus(theirs, sentThem), "me", minus(mine, sentMe)))
				.with("stale", Map.of("them", minus(sentThem, theirs), "me", minus(sentMe, mine)));
		}
		Instant now = clock.instant();
		List<Envelope> queued = new ArrayList<>();
		for (Outgoing o : outgoing) {
			byte[] ciphertext = bytes("ciphertext", o.ciphertext(), 1, MAX_CIPHERTEXT);
			if (o.type() == null) {
				throw ApiException.badRequest("INVALID_ENVELOPES", "Each envelope needs a type");
			}
			queued.add(new Envelope(messageId, conversationId, senderId, senderDevice,
					o.toSelf() ? senderId : recipientId, o.deviceId(), o.type(), ciphertext, now));
		}
		envelopes.saveAll(queued);
		Ping ping = new Ping(conversationId, messageId);
		realtime.toUser(recipientId, "e2ee", ping);
		realtime.toUser(senderId, "e2ee", ping);
	}

	// ---- inbox -----------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<InboxItem> inbox(String userId, String sessionId, int deviceId, int limit) {
		requireOwn(userId, sessionId, deviceId);
		return envelopes.inbox(userId, deviceId, PageRequest.of(0, Math.clamp(limit, 1, 200)))
			.stream()
			.map(e -> new InboxItem(e.getId(), e.getConversationId(), e.getMessageId(), e.getSenderUserId().equals(userId),
					e.getSenderDeviceId(), e.getType(), Base64.getEncoder().encodeToString(e.getCiphertext()),
					e.getCreatedAt()))
			.toList();
	}

	/** Deletes delivered envelopes: the server keeps no copy once the device has it. */
	@Transactional
	public int acknowledge(String userId, String sessionId, int deviceId, List<String> envelopeIds) {
		requireOwn(userId, sessionId, deviceId);
		if (envelopeIds == null || envelopeIds.isEmpty()) {
			return 0;
		}
		return envelopes.acknowledge(userId, deviceId, envelopeIds.stream().limit(500).toList());
	}

	// ---- lifecycle ---------------------------------------------------------------------------------------

	@Transactional
	public void deleteForConversations(Collection<String> conversationIds) {
		if (!conversationIds.isEmpty()) {
			envelopes.deleteForConversations(conversationIds);
		}
	}

	@Scheduled(fixedDelayString = "${oneday.e2ee.sweep-interval:PT1H}", initialDelayString = "PT5M")
	@Transactional
	public int purgeUndelivered() {
		return envelopes.deleteOlderThan(clock.instant().minus(ENVELOPE_RETENTION));
	}

	@Transactional(readOnly = true)
	public List<DeviceView> export(String userId) {
		return devices.findByUser(userId).stream().map(this::view).toList();
	}

	@Transactional
	public void forget(String userId) {
		envelopes.deleteInvolving(userId);
		preKeys.deleteByUser(userId);
		devices.deleteByUser(userId);
	}

	// ---- internals ---------------------------------------------------------------------------------------

	/** Live devices only. A device whose sign-in session ended is unlinked on sight. */
	private List<E2eeDevice> linked(String userId) {
		List<E2eeDevice> live = new ArrayList<>();
		for (E2eeDevice device : devices.findByUser(userId)) {
			if (liveness.isLive(device.getSessionId())) {
				live.add(device);
			}
			else {
				remove(device.getKey());
			}
		}
		return live;
	}

	private void remove(E2eeDevice.Key key) {
		envelopes.deleteForDevice(key.userId(), key.deviceId());
		preKeys.deleteForDevice(key.userId(), key.deviceId());
		devices.deleteById(key);
	}

	private E2eeDevice requireOwn(String userId, String sessionId, int deviceId) {
		guard.requireExisting(userId);
		E2eeDevice device = devices.findById(new E2eeDevice.Key(userId, deviceId))
			.orElseThrow(() -> ApiException.notFound("Device"));
		if (!device.getSessionId().equals(sessionId)) {
			throw ApiException.forbidden("DEVICE_NOT_THIS_SESSION", "That device belongs to another sign-in");
		}
		return device;
	}

	private void addPreKeys(E2eeDevice device, List<PreKey> batch) {
		if (batch == null || batch.isEmpty()) {
			return;
		}
		if (batch.size() > MAX_PREKEYS_PER_UPLOAD) {
			throw ApiException.badRequest("TOO_MANY_PREKEYS", "Upload at most 100 prekeys at a time");
		}
		E2eeDevice.Key key = device.getKey();
		if (preKeys.countFor(key.userId(), key.deviceId()) + batch.size() > MAX_PREKEYS_STORED) {
			throw ApiException.unprocessable("PREKEY_LIMIT", "Enough prekeys are stored already");
		}
		List<OneTimePreKey> rows = new ArrayList<>();
		for (PreKey p : batch) {
			requireKeyId(p.keyId());
			rows.add(new OneTimePreKey(new OneTimePreKey.Key(key.userId(), key.deviceId(), p.keyId()),
					key("oneTimePreKey", p.publicKey())));
		}
		if (rows.stream().map(r -> r.getKey().keyId()).distinct().count() != rows.size()) {
			throw ApiException.badRequest("DUPLICATE_PREKEY", "Prekey ids must be unique");
		}
		preKeys.saveAll(rows);
	}

	private Bundle bundle(E2eeDevice device) {
		E2eeDevice.Key key = device.getKey();
		PreKey oneTime = null;
		for (int attempt = 0; attempt < 3 && oneTime == null; attempt++) {
			List<OneTimePreKey> candidates = preKeys.findAvailable(key.userId(), key.deviceId(), PageRequest.of(0, 1));
			if (candidates.isEmpty()) {
				break;
			}
			OneTimePreKey candidate = candidates.get(0);
			if (preKeys.claim(candidate.getKey()) == 1) {
				oneTime = new PreKey(candidate.getKey().keyId(), b64(candidate.getPublicKey()));
			}
		}
		return new Bundle(device.deviceId(), device.getRegistrationId(), b64(device.getIdentityKey()),
				new SignedPreKey(device.getSignedPreKeyId(), b64(device.getSignedPreKey()),
						b64(device.getSignedPreKeySignature())),
				oneTime);
	}

	private DeviceView view(E2eeDevice device) {
		E2eeDevice.Key key = device.getKey();
		return new DeviceView(device.deviceId(), device.getCreatedAt(), preKeys.countFor(key.userId(), key.deviceId()));
	}

	private static SignedPreKey requireSignedPreKey(SignedPreKey spk) {
		if (spk == null) {
			throw ApiException.badRequest("SIGNED_PREKEY_REQUIRED", "A signed prekey is required");
		}
		requireKeyId(spk.keyId());
		return spk;
	}

	private static void requireKeyId(int keyId) {
		if (keyId < 0 || keyId > 0xFFFFFF) {
			throw ApiException.badRequest("INVALID_KEY_ID", "Key ids are 0-16777215");
		}
	}

	private static byte[] key(String field, String value) {
		return bytes(field, value, 32, 65);
	}

	private static byte[] bytes(String field, String value, int min, int max) {
		byte[] decoded;
		try {
			decoded = value == null ? null : Base64.getDecoder().decode(value);
		}
		catch (IllegalArgumentException ex) {
			decoded = null;
		}
		if (decoded == null || decoded.length < min || decoded.length > max) {
			throw ApiException.badRequest("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(),
					field + " must be base64, " + min + "-" + max + " bytes");
		}
		return decoded;
	}

	private static String b64(byte[] value) {
		return Base64.getEncoder().encodeToString(value);
	}

	private static Set<Integer> ids(List<E2eeDevice> list) {
		return list.stream().map(E2eeDevice::deviceId).collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<Integer> targets(List<Outgoing> outgoing, boolean toSelf) {
		return outgoing.stream()
			.filter(o -> o.toSelf() == toSelf)
			.map(Outgoing::deviceId)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<Integer> minus(Set<Integer> a, Set<Integer> b) {
		Set<Integer> out = new TreeSet<>(a);
		out.removeAll(b);
		return out;
	}

	// ---- API shapes ------------------------------------------------------------------------------------

	public record PreKey(int keyId, String publicKey) {
	}

	public record SignedPreKey(int keyId, String publicKey, String signature) {
	}

	public record NewDevice(int registrationId, String identityKey, SignedPreKey signedPreKey,
			List<PreKey> oneTimePreKeys) {
	}

	/** {@code oneTimePreKeysLeft}: the app tops up below a threshold. */
	public record DeviceView(int deviceId, Instant linkedAt, long oneTimePreKeysLeft) {
	}

	/** {@code oneTimePreKey} is null when the device ran out (X3DH then uses the signed prekey only). */
	public record Bundle(int deviceId, int registrationId, String identityKey, SignedPreKey signedPreKey,
			PreKey oneTimePreKey) {
	}

	/** One envelope to send: to one of the other person's devices, or ({@code toSelf}) one of the sender's. */
	public record Outgoing(boolean toSelf, int deviceId, EnvelopeType type, String ciphertext) {
	}

	/** {@code mine}: sent from one of the reader's own other devices. */
	public record InboxItem(String envelopeId, String conversationId, String messageId, boolean mine, int senderDevice,
			EnvelopeType type, String ciphertext, Instant sentAt) {
	}

	/** Socket hint that an envelope is waiting; the content is fetched from the device's inbox. */
	public record Ping(String conversationId, String messageId) {
	}
}
