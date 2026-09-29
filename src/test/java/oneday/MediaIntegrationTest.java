package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.jayway.jsonpath.JsonPath;

import oneday.media.DevMediaStorage;
import oneday.support.ApiTestSupport;
import oneday.support.TestMedia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Upload tickets, ownership of media, short-lived URLs, and deletion of objects. */
class MediaIntegrationTest extends ApiTestSupport {

	@Autowired
	private DevMediaStorage storage;

	@Test
	void uploadTicketsAreVerifiedOnlyRandomAndConstrained() throws Exception {
		String unverified = register("Newbie");
		postAs(unverified, "/media/uploads", "{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1000}")
			.andExpect(status().isForbidden());

		String token = verifiedUser("Asha");
		String ticket = body(postAs(token, "/media/uploads",
				"{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":250000}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.method").value("PUT"))
			.andExpect(jsonPath("$.headers.content-type").value("image/jpeg"))
			.andExpect(jsonPath("$.uploadUrl", startsWith(DEV_MEDIA + "incoming/"))));
		String mediaRef = JsonPath.read(ticket, "$.mediaRef");
		assertThat(mediaRef).matches("moments/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg").doesNotContain(userIdOf(token));

		postAs(token, "/media/uploads", "{\"kind\":\"PHOTO\",\"contentType\":\"image/gif\",\"sizeBytes\":1000}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		postAs(token, "/media/uploads", "{\"kind\":\"PHOTO\",\"contentType\":\"video/mp4\",\"sizeBytes\":1000}")
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		postAs(token, "/media/uploads", "{\"kind\":\"PHOTO\",\"contentType\":\"image/png\",\"sizeBytes\":20971520}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"));
		if (TestMedia.ffmpegInstalled()) {
			postAs(token, "/media/uploads", "{\"kind\":\"VIDEO\",\"contentType\":\"video/mp4\",\"sizeBytes\":52428800}")
				.andExpect(status().isCreated());
		}
	}

	@Test
	void momentsCanOnlyAttachYourOwnRecentUnusedUpload() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		String ashasPhoto = upload(asha, "PHOTO", "image/jpeg");

		String stealing = friendsOnly("PHOTO", ashasPhoto);
		postAs(ravi, "/moments", stealing).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
		postAs(asha, "/moments", friendsOnly("VIDEO", ashasPhoto)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
		postAs(asha, "/moments", friendsOnly("PHOTO", "moments/2026/09/made-up.jpg"))
			.andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));

		postAs(asha, "/moments", friendsOnly("PHOTO", ashasPhoto)).andExpect(status().isCreated());
		postAs(asha, "/moments", friendsOnly("PHOTO", ashasPhoto)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEDIA_ALREADY_USED"));

		String stale = upload(asha, "PHOTO", "image/jpeg");
		clock.advance(Duration.ofHours(25));
		postAs(asha, "/moments", friendsOnly("PHOTO", stale)).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
	}

	@Test
	void strangersSeeOnlyTheOptionalPreviewNeverTheMedia() throws Exception {
		assumeTrue(TestMedia.ffmpegInstalled(), "video processing needs ffmpeg");
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		locate(asha, BLR_LAT, BLR_LON);
		locate(ravi, BLR_LAT, BLR_LON);
		String video = upload(asha, "VIDEO", "video/mp4");
		String momentId = JsonPath.read(body(postAs(asha, "/moments", """
				{"kind":"VIDEO","mediaRef":"%s","activityTag":"football","shareScope":"PUBLIC_DISCOVERY",
				 "capturedLive":true,"previewAllowed":true}""".formatted(video)).andExpect(status().isCreated())),
				"$.id");

		getAs(ravi, "/moments/" + momentId).andExpect(jsonPath("$.layer").value("AMBIENT"))
			.andExpect(jsonPath("$.mediaUrl").value(nullValue()))
			.andExpect(jsonPath("$.previewUrl", containsString(".preview.mp4?expires=")));
		getAs(ravi, "/discover/constellation")
			.andExpect(jsonPath("$.nodes[0].previewUrl", containsString(".preview.mp4?expires=")));
		getAs(asha, "/moments/" + momentId)
			.andExpect(jsonPath("$.mediaUrl", startsWith(DEV_MEDIA + video + "?expires=")));
	}

	@Test
	void deletingAMomentOrTheAccountDeletesTheObjects() throws Exception {
		String asha = verifiedUser("Asha");
		String first = upload(asha, "PHOTO", "image/jpeg");
		String momentId = JsonPath.read(body(postAs(asha, "/moments", friendsOnly("PHOTO", first))), "$.id");
		deleteAs(asha, "/moments/" + momentId).andExpect(status().isNoContent());
		assertThat(storage.deletedKeys()).contains(first, first + ".preview.mp4");

		String second = upload(asha, "PHOTO", "image/jpeg");
		postAs(asha, "/moments", friendsOnly("PHOTO", second)).andExpect(status().isCreated());
		String unused = upload(asha, "PHOTO", "image/png");
		deleteAs(asha, "/privacy/account?confirm=DELETE").andExpect(status().isNoContent());
		assertThat(storage.deletedKeys()).contains(second, unused);
	}

	private static String friendsOnly(String kind, String mediaRef) {
		return "{\"kind\":\"" + kind + "\",\"mediaRef\":\"" + mediaRef + "\",\"shareScope\":\"FRIENDS_ONLY\"}";
	}
}
