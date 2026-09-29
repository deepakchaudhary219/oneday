package oneday.privacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import oneday.chat.ChatService;
import oneday.connections.Connection;
import oneday.connections.ConnectionService;
import oneday.geo.LocationService;
import oneday.identity.AccountAdministration;
import oneday.identity.AuthService;
import oneday.identity.User;
import oneday.identity.UserGuard;
import oneday.media.MediaService;
import oneday.notify.NotificationService;
import oneday.moments.MomentService;
import oneday.profile.ProfileService;
import oneday.safety.SafetyService;
import oneday.signals.SignalService;
import oneday.staff.StaffDirectory;
import oneday.verification.VerificationService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service data rights (DPDP Act / GDPR; blueprint §47.5): export everything tied to the account,
 * or erase it, without a support ticket. The privacy claims elsewhere depend on this being easy.
 */
@Service
public class PrivacyService {

	private final UserGuard guard;

	private final AuthService accounts;

	private final ProfileService profiles;

	private final LocationService locations;

	private final MomentService moments;

	private final SignalService signals;

	private final ConnectionService connections;

	private final ChatService chat;

	private final SafetyService safety;

	private final VerificationService verification;

	private final StaffDirectory staff;

	private final MediaService media;

	private final AccountAdministration administration;

	private final NotificationService notifications;

	public PrivacyService(UserGuard guard, AuthService accounts, ProfileService profiles, LocationService locations,
			MomentService moments, SignalService signals, ConnectionService connections, ChatService chat,
			SafetyService safety, VerificationService verification, StaffDirectory staff, MediaService media,
			AccountAdministration administration, NotificationService notifications) {
		this.guard = guard;
		this.accounts = accounts;
		this.profiles = profiles;
		this.locations = locations;
		this.moments = moments;
		this.signals = signals;
		this.connections = connections;
		this.chat = chat;
		this.safety = safety;
		this.verification = verification;
		this.staff = staff;
		this.media = media;
		this.administration = administration;
		this.notifications = notifications;
	}

	/** Everything we hold about the user. Other people's identities are not included. */
	@Transactional(readOnly = true)
	public Map<String, Object> export(String userId) {
		User user = guard.requireExisting(userId);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("account", row("email", user.getEmail(), "phone", user.getPhone(), "dateOfBirth",
				user.getDateOfBirth(), "createdAt", user.getCreatedAt(), "verificationStatus",
				user.getVerificationStatus(), "consentVersion", user.getConsentVersion(), "consentedAt",
				user.getConsentedAt()));
		data.put("profile", profiles.exportView(userId));
		data.put("location", locations.current(userId).orElse(null));
		data.put("moments", moments.allBy(userId)
			.stream()
			.map(m -> row("id", m.getId(), "kind", m.getKind(), "caption", m.getCaption(), "activity",
					m.getActivityTag(), "shareScope", m.getShareScope(), "area", m.getCell(), "createdAt",
					m.getCreatedAt(), "expiresAt", m.getExpiresAt()))
			.toList());
		data.put("signalsSent", signals.allSentBy(userId)
			.stream()
			.map(s -> row("momentId", s.getMomentId(), "reaction", s.getReaction(), "activityRef",
					s.getActivityRef(), "sentAt", s.getCreatedAt()))
			.toList());
		data.put("signalsReceived", signals.allReceivedBy(userId)
			.stream()
			.map(s -> row("reaction", s.getReaction(), "activityRef", s.getActivityRef(), "receivedAt",
					s.getCreatedAt(), "status", s.getStatus()))
			.toList());
		data.put("connections", connections.all(userId)
			.stream()
			.map(c -> row("id", c.getId(), "origin", c.getOrigin(), "state", c.getState(), "since",
					c.getCreatedAt(), "youSparked", c.hasSparked(userId)))
			.toList());
		data.put("messagesSent", chat.sentBy(userId)
			.stream()
			.map(m -> row("conversationId", m.getConversationId(), "body", m.getBody(), "sentAt", m.getCreatedAt()))
			.toList());
		data.put("blocksMade", safety.blocksMadeBy(userId).stream().map(b -> row("at", b.getCreatedAt())).toList());
		data.put("reportsFiled", safety.reportsFiledBy(userId)
			.stream()
			.map(r -> row("category", r.getCategory(), "status", r.getStatus(), "at", r.getCreatedAt()))
			.toList());
		data.put("devices", notifications.devicesOf(userId));
		data.put("notices", notifications.notices(userId));
		data.put("verificationAttempts", verification.attemptsBy(userId)
			.stream()
			.map(a -> row("outcome", a.getOutcome(), "at", a.getCreatedAt()))
			.toList());
		return data;
	}

	/**
	 * The holder's erasure request. Normally everything is erased at once. If the account is under a safety
	 * hold (open P0 report, or enforcement within the evidence-retention period), the account disappears
	 * immediately (identifiers released, invisible, logged out) but its records are kept until the hold
	 * lifts, then erased by {@link #completePendingErasures()}. The response is identical either way, so a
	 * person under investigation is not tipped off.
	 */
	@Transactional
	public void erase(String userId) {
		guard.requireExisting(userId);
		if (safety.holdReason(userId).isPresent()) {
			administration.deactivateForErasure(userId);
			locations.forget(userId);
			connections.endAllFor(userId);
			return;
		}
		eraseNow(userId);
	}

	/** Finishes erasures that were deferred by a hold which has since lifted. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
	@Transactional
	public void completePendingErasures() {
		administration.pendingErasures()
			.stream()
			.filter(u -> safety.holdReason(u.getId()).isEmpty())
			.forEach(u -> eraseNow(u.getId()));
	}

	/**
	 * Erases the account and everything attached to it. Conversations are removed for both participants.
	 * Reports the user filed are kept with the reporter detached; reports about the user are retained as
	 * required for safety and legal obligations.
	 */
	private void eraseNow(String userId) {
		List<Connection> all = connections.all(userId);
		chat.deleteForConnections(all.stream().map(Connection::getId).toList());
		connections.deleteAll(all);
		signals.deleteInvolving(userId);
		moments.deleteAllBy(userId);
		media.deleteAllFor(userId);
		locations.forget(userId);
		safety.forget(userId);
		verification.forget(userId);
		staff.forget(userId);
		notifications.forget(userId);
		profiles.delete(userId);
		accounts.deleteAccount(userId);
	}

	private static Map<String, Object> row(Object... keyValues) {
		Map<String, Object> row = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			row.put((String) keyValues[i], keyValues[i + 1]);
		}
		return row;
	}
}
