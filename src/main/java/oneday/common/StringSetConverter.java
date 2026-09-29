package oneday.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores small, already-normalised sets (activities, values, languages) as a comma-delimited column.
 * Good enough while filtering happens in the application; normalise into join tables once a set is
 * needed in SQL predicates (implementation plan §4).
 */
@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

	@Override
	public String convertToDatabaseColumn(Set<String> values) {
		return values == null || values.isEmpty() ? null : String.join(",", values);
	}

	@Override
	public Set<String> convertToEntityAttribute(String column) {
		Set<String> values = new LinkedHashSet<>();
		if (column != null && !column.isBlank()) {
			Arrays.stream(column.split(",")).filter(s -> !s.isBlank()).forEach(values::add);
		}
		return values;
	}
}
