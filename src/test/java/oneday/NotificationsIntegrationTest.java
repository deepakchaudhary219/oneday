package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.notify.DevPushSender;
import oneday.notify.LocalPulseScheduler;
import oneday.notify.PushSender.PushMessage;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The daily Local Pulse and safety notices: honest, predictable, discreet. */
class NotificationsIntegrationTest extends ApiTestSupport {

	@Autowired
	private LocalPulseScheduler scheduler;

	@Autowired
	private DevPushSender push;

	@Test
	void thePulseArrivesOncePerDayAtTheChosenHourWithRealContent() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String device = "device-" + userIdOf(asha);
		registerDevice(asha, device);
		updateProfile(asha, "{\"pulseHour\":21}");
		advanceTo(21, "Asia/Kolkata");

		locate(asha, BLR_LAT, BLR_LON);
		sendSignal(ravi, postPublicMoment(asha, "coffee"), null);

		scheduler.deliverDue();
		assertThat(push.sentTo(device)).extracting(PushMessage::title, PushMessage::body)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("Your Local Pulse", "1 person sent you a signal"));

		// The job runs every few minutes; the user still gets one pulse that day.
		clock.advance(Duration.ofMinutes(20));
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).hasSize(1);

		// Next day, same hour: the signal is still in its window, so there is still something real to say.
		advanceTo(21, "Asia/Kolkata");
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).hasSize(2);
	}

	@Test
	void noPushAtOtherHoursOrWhenThereIsNothingRealToSay() throws Exception {
		String asha = verifiedUser("Asha");
		String device = "device-" + userIdOf(asha);
		registerDevice(asha, device);
		updateProfile(asha, "{\"pulseHour\":8}");

		advanceTo(20, "Asia/Kolkata");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		sendSignal(ravi, postPublicMoment(asha, "coffee"), null);
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).isEmpty();

		// Next morning at 08:00 the signal window has closed and nothing else happened: stay quiet.
		clock.advance(Duration.ofHours(49));
		advanceTo(8, "Asia/Kolkata");
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).isEmpty();
	}

	@Test
	void discretionModeKeepsTheLockScreenNeutral() throws Exception {
		String asha = verifiedUser("Asha");
		String device = "device-" + userIdOf(asha);
		registerDevice(asha, device);
		updateProfile(asha, "{\"pulseHour\":18,\"discretionMode\":true}");
		advanceTo(18, "Asia/Kolkata");
		locate(asha, BLR_LAT, BLR_LON);
		sendSignal(verifiedUser("Ravi"), postPublicMoment(asha, "coffee"), null);

		scheduler.deliverDue();
		assertThat(push.sentTo(device)).extracting(PushMessage::title, PushMessage::body)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("Update", "You have an update"));
	}

	@Test
	void eachUserIsReachedInTheirOwnTimeZone() throws Exception {
		String priya = verifiedUser("Priya");
		String device = "device-" + userIdOf(priya);
		registerDevice(priya, device);
		updateProfile(priya, "{\"pulseHour\":9,\"timeZone\":\"America/New_York\"}");
		perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/profile/me"), priya,
				"{\"timeZone\":\"Mars/Olympus\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));

		advanceTo(9, "Asia/Kolkata");
		locate(priya, BLR_LAT, BLR_LON);
		sendSignal(verifiedUser("Ravi"), postPublicMoment(priya, "coffee"), null);
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).isEmpty();

		advanceTo(9, "America/New_York");
		scheduler.deliverDue();
		assertThat(push.sentTo(device)).hasSize(1);
	}

	@Test
	void devicesFollowTheSignedInAccountAndAreCapped() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String shared = "shared-phone-" + userIdOf(asha);
		registerDevice(asha, shared);
		registerDevice(ravi, shared);
		assertThat(countDevices(userIdOf(asha))).isZero();
		assertThat(countDevices(userIdOf(ravi))).isEqualTo(1);

		for (int i = 0; i < 7; i++) {
			clock.advance(Duration.ofSeconds(1));
			registerDevice(ravi, "ravi-" + i + "-" + userIdOf(ravi));
		}
		assertThat(countDevices(userIdOf(ravi))).isEqualTo(5);
		getAs(ravi, "/privacy/export").andExpect(jsonPath("$.devices", hasSize(5)))
			.andExpect(jsonPath("$.devices[0].pushToken").doesNotExist());

		postAs(ravi, "/devices/unregister", "{\"pushToken\":\"ravi-6-" + userIdOf(ravi) + "\"}")
			.andExpect(status().isNoContent());
		assertThat(countDevices(userIdOf(ravi))).isEqualTo(4);
	}

	@Test
	void reportOutcomesReachTheRightPeopleWithoutRevealingPenalties() throws Exception {
		String moderator = promote(verifiedUser("Mod"), "MODERATOR");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String raviDevice = "device-" + userIdOf(ravi);
		registerDevice(ravi, raviDevice);
		locate(ravi, BLR_LAT, BLR_LON);
		String moment = postPublicMoment(ravi, "coffee");
		String harassment = report(asha, moment, "HARASSMENT");
		String spam = report(asha, moment, "SPAM");

		postAs(moderator, "/staff/reports/" + harassment + "/resolve", "{\"action\":\"WARN\"}")
			.andExpect(status().isOk());
		String warning = body(getAs(ravi, "/notices").andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].kind").value("WARNING"))
			.andExpect(jsonPath("$[0].message", containsString("harassment")))
			.andExpect(jsonPath("$[0].read").value(false)));
		assertThat(push.sentTo(raviDevice)).isEmpty(); // queued in the outbox, sent after commit
		deliverEvents();
		assertThat(push.sentTo(raviDevice)).extracting(PushMessage::title)
			.containsExactly("A message from OneDay Safety");
		postAs(ravi, "/notices/" + JsonPath.read(warning, "$[0].id") + "/read", null).andExpect(status().isNoContent());
		getAs(ravi, "/notices").andExpect(jsonPath("$[0].read").value(true));

		postAs(moderator, "/staff/reports/" + spam + "/resolve", "{\"action\":\"DISMISS\"}").andExpect(status().isOk());
		String reporterView = body(getAs(asha, "/notices").andExpect(jsonPath("$", hasSize(2))));
		List<String> messages = JsonPath.read(reporterView, "$[*].message");
		assertThat(messages).anyMatch(m -> m.contains("took action"))
			.anyMatch(m -> m.contains("didn't find a breach"))
			.noneMatch(m -> m.toLowerCase().contains("warn") || m.toLowerCase().contains("suspend"));
		getAs(ravi, "/notices").andExpect(jsonPath("$[*].message", not(org.hamcrest.Matchers.hasItem(
				containsString("Asha")))));
	}

	// ---- helpers ------------------------------------------------------------------------------------

	private void registerDevice(String token, String pushToken) throws Exception {
		postAs(token, "/devices", "{\"pushToken\":\"" + pushToken + "\",\"platform\":\"ANDROID\"}")
			.andExpect(status().isCreated());
	}

	/** Moves the clock forward to hh:05 local time in {@code zone} (today if still ahead, else tomorrow). */
	private void advanceTo(int hour, String zone) {
		ZonedDateTime now = clock.instant().atZone(ZoneId.of(zone));
		ZonedDateTime target = now.withHour(hour).withMinute(5).withSecond(0).withNano(0);
		if (!target.isAfter(now)) {
			target = target.plusDays(1);
		}
		clock.advance(Duration.between(now.toInstant(), target.toInstant()));
	}

	private String report(String reporter, String momentId, String category) throws Exception {
		return JsonPath.read(body(postAs(reporter, "/safety/reports",
				"{\"momentId\":\"" + momentId + "\",\"category\":\"" + category + "\"}")), "$.reportId");
	}

	private long countDevices(String userId) {
		Long n = jdbc.queryForObject("select count(*) from devices where user_id = ?", Long.class, userId);
		return n == null ? 0 : n;
	}
}
