package oneday.safety;

import java.util.Optional;
import java.util.function.BiFunction;

/** Small helper for declaring a {@link SafetyTargetResolver} as a bean. */
public final class SafetyTargets {

	private SafetyTargets() {
	}

	public static SafetyTargetResolver of(String field, String type, BiFunction<String, String, Optional<String>> resolve) {
		return new SafetyTargetResolver() {

			@Override
			public String field() {
				return field;
			}

			@Override
			public String type() {
				return type;
			}

			@Override
			public Optional<String> personBehind(String viewerId, String id) {
				return resolve.apply(viewerId, id);
			}
		};
	}
}
