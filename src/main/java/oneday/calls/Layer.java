package oneday.calls;

/** How much of themselves each person shares on a call, in order. */
public enum Layer {
	/** Voice only: every call starts here. */
	VOICE,
	/** Video, heavily blurred on the sender's device: presence and mood without a face. */
	BLURRED,
	/** Clear video. */
	CLEAR
}
