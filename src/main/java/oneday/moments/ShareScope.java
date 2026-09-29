package oneday.moments;

/** Chosen per post, independent of account privacy (blueprint v2 §3). */
public enum ShareScope {
	/** Friend Mode: only existing Connections see it; no Layered Reveal. */
	FRIENDS_ONLY,
	/** Discovery Mode: nearby strangers see the Layer-0 ambient view and may send a Signal. */
	PUBLIC_DISCOVERY
}
