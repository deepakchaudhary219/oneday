package oneday.safety;

import java.util.Optional;

/**
 * Lets any feature make its surfaces blockable and reportable without the safety module depending on it
 * (strategy pattern). A resolver maps an id the viewer can see on that surface (never someone's user id) to
 * the person behind it, or empty when the viewer has no business with that id.
 */
public interface SafetyTargetResolver {

	/** The request field and stored target type, e.g. {@code planId} / {@code PLAN}. */
	String field();

	String type();

	Optional<String> personBehind(String viewerId, String id);
}
