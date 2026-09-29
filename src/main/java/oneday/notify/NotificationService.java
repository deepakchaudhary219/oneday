package oneday.notify;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import oneday.common.ApiException;
import oneday.identity.UserGuard;
import oneday.notify.PushSender.PushMessage;
import oneday.profile.Profile;
import oneday.profile.ProfileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devices, pushes and in-app notices. Every push honours Discretion Mode: on a shared phone the lock
 * screen only ever says "You have an update", never names signals, people or safety topics.
 */
@Service
public class NotificationService {

	static final int MAX_DEVICES = 5;

	static final String DISCREET_TITLE = "Update";

	static final String DISCREET_BODY = "You have an update";

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final DeviceRepository devices;

	private final NoticeRepository notices;

	private final PulseDeliveryRepository deliveries;

	private final ObjectProvider<PushSender> push;

	private final ProfileService profiles;

	private final UserGuard guard;

	private final Clock clock;

	public NotificationService(DeviceRepository devices, NoticeRepository notices, PulseDeliveryRepository deliveries,
			ObjectProvider<PushSender> push, ProfileService profiles, UserGuard guard, Clock clock) {
		this.devices = devices;
		this.notices = notices;
		this.deliveries = deliveries;
		this.push = push;
		this.profiles = profiles;
		this.guard = guard;
		this.clock = clock;
	}

	@Transactional
	public DeviceView register(String userId, String pushToken, Device.Platform platform) {
		guard.requireActive(userId);
		Instant now = clock.instant();
		Device device = devices.findByPushToken(pushToken).orElse(null);
		if (device == null) {
			device = devices.save(new Device(userId, pushToken, platform, now));
		}
		else {
			device.assignTo(userId, platform, now);
		}
		// Keep the device just registered plus the most recently seen others; evict the rest.
		String current = device.getPushToken();
		List<Device> others = devices.findByUserIdOrderByLastSeenAtDesc(userId)
			.stream()
			.filter(d -> !d.getPushToken().equals(current))
			.toList();
		if (others.size() > MAX_DEVICES - 1) {
			devices.deleteAll(others.subList(MAX_DEVICES - 1, others.size()));
		}
		return new DeviceView(device.getPlatform(), device.getLastSeenAt());
	}

	@Transactional
	public void unregister(String userId, String pushToken) {
		devices.findByPushToken(pushToken).filter(d -> d.getUserId().equals(userId)).ifPresent(devices::delete);
	}

	/** Sends to all of the user's devices, rewritten neutrally if Discretion Mode is on. */
	@Transactional(readOnly = true)
	public int pushToUser(String userId, String title, String body, Map<String, String> data) {
		PushSender sender = push.getIfAvailable();
		List<Device> targets = devices.findByUserIdOrderByLastSeenAtDesc(userId);
		if (sender == null || targets.isEmpty()) {
			return 0;
		}
		boolean discreet = profiles.find(userId).map(Profile::isDiscretionMode).orElse(true);
		PushMessage message = discreet ? new PushMessage(DISCREET_TITLE, DISCREET_BODY, data)
				: new PushMessage(title, body, data);
		int delivered = 0;
		for (Device device : targets) {
			try {
				sender.send(device.getPushToken(), message);
				delivered++;
			}
			catch (RuntimeException ex) {
				log.warn("Push to a device failed", ex);
			}
		}
		return delivered;
	}

	/** Stores an in-app notice and pushes a short pointer to it. */
	@Transactional
	public void notice(String userId, Notice.Kind kind, String message) {
		if (profiles.find(userId).isEmpty()) {
			return;
		}
		Notice notice = notices.save(new Notice(userId, kind, message, clock.instant()));
		pushToUser(userId, "A message from OneDay Safety", "Tap to read it in the app",
				Map.of("open", "notices", "noticeId", notice.getId()));
	}

	@Transactional(readOnly = true)
	public List<NoticeView> notices(String userId) {
		guard.requireExisting(userId);
		return notices.findByUserIdOrderByCreatedAtDesc(userId).stream().map(NoticeView::of).toList();
	}

	@Transactional
	public void markRead(String userId, String noticeId) {
		Notice notice = notices.findById(noticeId)
			.filter(n -> n.getUserId().equals(userId))
			.orElseThrow(() -> ApiException.notFound("Notice"));
		notice.markRead(clock.instant());
	}

	@Transactional(readOnly = true)
	public List<DeviceView> devicesOf(String userId) {
		return devices.findByUserIdOrderByLastSeenAtDesc(userId)
			.stream()
			.map(d -> new DeviceView(d.getPlatform(), d.getLastSeenAt()))
			.toList();
	}

	/** Erasure: devices, notices and pulse history. */
	@Transactional
	public void forget(String userId) {
		devices.deleteAll(devices.findByUserIdOrderByLastSeenAtDesc(userId));
		notices.deleteByUserId(userId);
		deliveries.deleteByUserId(userId);
	}

	/** Push tokens are never echoed back: only the platform and when the device was last seen. */
	public record DeviceView(Device.Platform platform, Instant lastSeenAt) {
	}

	public record NoticeView(String id, Notice.Kind kind, String message, Instant createdAt, boolean read) {

		static NoticeView of(Notice n) {
			return new NoticeView(n.getId(), n.getKind(), n.getMessage(), n.getCreatedAt(), n.getReadAt() != null);
		}
	}
}
