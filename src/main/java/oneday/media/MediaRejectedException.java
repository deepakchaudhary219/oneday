package oneday.media;

/** The upload is not acceptable (wrong type, damaged, too large). The message is shown to the uploader. */
class MediaRejectedException extends Exception {

	MediaRejectedException(String reason) {
		super(reason);
	}
}
