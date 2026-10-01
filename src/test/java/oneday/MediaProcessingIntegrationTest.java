package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import oneday.media.DevMediaStorage;
import oneday.media.MediaService;
import oneday.support.ApiTestSupport;
import oneday.support.TestMedia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The media worker: nothing a user uploads is served until it has been re-encoded without metadata. */
class MediaProcessingIntegrationTest extends ApiTestSupport {

	@Autowired
	private DevMediaStorage storage;

	@Autowired
	private MediaService mediaService;

	@Test
	void photoMetadataIsStrippedAndOrientationIsApplied() throws Exception {
		String asha = verifiedUser("Asha");
		// 80×40 pixels stored with EXIF orientation 6: the phone was held upright, so it displays as 40×80.
		byte[] original = TestMedia.jpegWithMetadata(80, 40, 6);
		assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains(TestMedia.SECRET);

		String ref = uploadBytes(asha, "PHOTO", "image/jpeg", original, true);
		uploadStatus(asha, ref).andExpect(jsonPath("$.status").value("READY"));
		byte[] served = fetchFromDevStorage(mediaUrlOfNewMoment(asha, ref, "PHOTO"));

		String text = new String(served, StandardCharsets.ISO_8859_1);
		assertThat(text).doesNotContain(TestMedia.SECRET).doesNotContain("Exif");
		assertThat(served[0] & 0xFF).isEqualTo(0xFF);
		assertThat(served[1] & 0xFF).isEqualTo(0xD8);
		BufferedImage image = TestMedia.decode(served);
		assertThat(image.getWidth()).isEqualTo(40);
		assertThat(image.getHeight()).isEqualTo(80);
		// The original upload is gone; only the cleaned object exists.
		assertThat(storage.contains(ref.replace("moments/", "incoming/"))).isFalse();
		assertThat(storage.contains(ref)).isTrue();
	}

	@Test
	void pngsAreServedAsJpeg() throws Exception {
		String asha = verifiedUser("Asha");
		String ref = uploadBytes(asha, "PHOTO", "image/png", TestMedia.png(30, 20), true);
		assertThat(ref).endsWith(".jpg");
		byte[] served = fetchFromDevStorage(mediaUrlOfNewMoment(asha, ref, "PHOTO"));
		assertThat(served[0] & 0xFF).isEqualTo(0xFF);
		assertThat(TestMedia.decode(served).getWidth()).isEqualTo(30);
	}

	@Test
	void badFilesAreRejectedWithAReasonAndCanNeverBeAttached() throws Exception {
		String asha = verifiedUser("Asha");
		String notAnImage = uploadBytes(asha, "PHOTO", "image/jpeg", "hello, not a photo".getBytes(), true);
		uploadStatus(asha, notAnImage).andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectReason").value("That file isn't a photo we can read"));
		postAs(asha, "/moments", friendsOnly("PHOTO", notAnImage)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEDIA_REJECTED"));

		// 48 megapixels in a few kilobytes: refused before decoding.
		String bomb = uploadBytes(asha, "PHOTO", "image/png", TestMedia.pixelBombPng(8000, 6000), true);
		uploadStatus(asha, bomb).andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectReason").value("That photo has too many pixels"));
	}

	@Test
	void mediaIsOnlyAttachableOnceProcessed() throws Exception {
		String asha = verifiedUser("Asha");
		String uploadedNotCompleted = uploadBytes(asha, "PHOTO", "image/jpeg", TestMedia.jpeg(20, 20), false);
		uploadStatus(asha, uploadedNotCompleted).andExpect(jsonPath("$.status").value("AWAITING_UPLOAD"));
		postAs(asha, "/moments", friendsOnly("PHOTO", uploadedNotCompleted)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEDIA_NOT_READY"));

		// Completing without ever uploading the file is rejected, not left hanging.
		String ticket = body(postAs(asha, "/media/uploads",
				"{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1000}"));
		String neverUploaded = JsonPath.read(ticket, "$.mediaRef");
		postAs(asha, "/media/uploads/complete", "{\"mediaRef\":\"" + neverUploaded + "\"}").andExpect(status().isOk());
		uploadStatus(asha, neverUploaded).andExpect(jsonPath("$.status").value("REJECTED"));

		// Someone else's upload is invisible.
		getAs(verifiedUser("Ravi"), "/media/uploads/status?mediaRef=" + uploadedNotCompleted)
			.andExpect(status().isNotFound());
	}

	@Test
	void signedUploadUrlsCannotBeTamperedWith() throws Exception {
		String asha = verifiedUser("Asha");
		byte[] photo = TestMedia.jpeg(20, 20);
		String ticket = body(postAs(asha, "/media/uploads",
				"{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":" + photo.length + "}"));
		String url = JsonPath.read(ticket, "$.uploadUrl");

		putToDevStorage(url, "image/png", photo).andExpect(status().isForbidden());
		byte[] bigger = new byte[photo.length + 10];
		putToDevStorage(url, "image/jpeg", bigger).andExpect(status().isForbidden());
		putToDevStorage(url.replaceAll("sig=[0-9a-f]+", "sig=00000000000000000000000000000000"), "image/jpeg", photo)
			.andExpect(status().isForbidden());
		clock.advance(Duration.ofMinutes(11));
		putToDevStorage(url, "image/jpeg", photo).andExpect(status().isForbidden());
	}

	@Test
	void theSweepRecoversLostWorkAndClearsAbandonedTickets() throws Exception {
		String asha = verifiedUser("Asha");
		String lost = uploadBytes(asha, "PHOTO", "image/jpeg", TestMedia.jpeg(20, 20), false);
		// Simulate a worker that crashed mid-processing a while ago.
		jdbc.update("update media_uploads set status = 'PROCESSING', updated_at = ? where object_key = ?",
				java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(30))), lost);
		String abandoned = JsonPath.read(body(postAs(asha, "/media/uploads",
				"{\"kind\":\"PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1000}")), "$.mediaRef");

		clock.advance(Duration.ofHours(25));
		mediaService.sweep();

		uploadStatus(asha, lost).andExpect(jsonPath("$.status").value("READY"));
		uploadStatus(asha, abandoned).andExpect(status().isNotFound());
	}

	@Test
	void videoMetadataIsStrippedAndThePreviewIsSilentAndShort() throws Exception {
		assumeTrue(TestMedia.ffmpegInstalled(), "video processing needs ffmpeg");
		String asha = verifiedUser("Asha");
		byte[] original = TestMedia.mp4WithMetadata(6);
		assertThat(TestMedia.ffprobe(original)).contains("+12.9716+077.5946").contains(TestMedia.SECRET);

		String ref = uploadBytes(asha, "VIDEO", "video/mp4", original, true);
		uploadStatus(asha, ref).andExpect(jsonPath("$.status").value("READY"));
		String view = body(postAs(asha, "/moments", """
				{"kind":"VIDEO","mediaRef":"%s","shareScope":"FRIENDS_ONLY","previewAllowed":true}""".formatted(ref))
			.andExpect(status().isCreated()));

		String video = TestMedia.ffprobe(fetchFromDevStorage(JsonPath.read(view, "$.mediaUrl")));
		assertThat(video).doesNotContain("12.9716").doesNotContain(TestMedia.SECRET);
		java.util.Map<String, Object> tags = JsonPath.read(video, "$.format.tags");
		assertThat(tags.keySet()).noneMatch(k -> k.toLowerCase().contains("location")).doesNotContain("title");
		List<String> videoCodecs = JsonPath.read(video, "$.streams[*].codec_type");
		assertThat(videoCodecs).contains("video", "audio");

		String preview = TestMedia.ffprobe(fetchFromDevStorage(JsonPath.read(view, "$.previewUrl")));
		List<String> previewCodecs = JsonPath.read(preview, "$.streams[*].codec_type");
		assertThat(previewCodecs).containsExactly("video");
		assertThat(Double.parseDouble(JsonPath.read(preview, "$.format.duration"))).isLessThanOrEqualTo(4.1);
		assertThat((Integer) JsonPath.read(preview, "$.streams[0].width")).isEqualTo(360);
		assertThat(preview).doesNotContain("12.9716").doesNotContain(TestMedia.SECRET);
	}

	private org.springframework.test.web.servlet.ResultActions uploadStatus(String token, String mediaRef)
			throws Exception {
		return getAs(token, "/media/uploads/status?mediaRef=" + mediaRef);
	}

	private String mediaUrlOfNewMoment(String token, String ref, String kind) throws Exception {
		return JsonPath.read(body(postAs(token, "/moments", friendsOnly(kind, ref)).andExpect(status().isCreated())),
				"$.mediaUrl");
	}

	private static String friendsOnly(String kind, String mediaRef) {
		return "{\"kind\":\"" + kind + "\",\"mediaRef\":\"" + mediaRef + "\",\"shareScope\":\"FRIENDS_ONLY\"}";
	}
}
