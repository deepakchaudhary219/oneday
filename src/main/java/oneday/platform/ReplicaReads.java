package oneday.platform;

import java.util.function.Supplier;

/**
 * Opt-in read-replica routing. Only code wrapped in {@link #run} <em>and</em> running in a read-only
 * transaction goes to the replica; everything else (all writes, and reads that must see the caller's own
 * writes, such as session checks) stays on the primary. Use it for heavy, staleness-tolerant reads: the
 * Constellation, the Story Map, Heat, Today's Prompt, the Ledger.
 */
public final class ReplicaReads {

	private static final ThreadLocal<Boolean> ALLOWED = ThreadLocal.withInitial(() -> false);

	private ReplicaReads() {
	}

	public static <T> T run(Supplier<T> work) {
		boolean outer = ALLOWED.get();
		ALLOWED.set(true);
		try {
			return work.get();
		}
		finally {
			ALLOWED.set(outer);
		}
	}

	static boolean allowed() {
		return ALLOWED.get();
	}
}
