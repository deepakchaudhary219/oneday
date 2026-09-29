package oneday.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IdsTest {

	@Test
	void generatesUniqueVersion7Uuids() {
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String id = Ids.newId();
			UUID uuid = UUID.fromString(id);
			assertThat(uuid.version()).isEqualTo(7);
			assertThat(uuid.variant()).isEqualTo(2);
			ids.add(id);
		}
		assertThat(ids).hasSize(1000);
	}

	@Test
	void idsFromLaterMillisecondsSortAfterEarlierOnes() throws InterruptedException {
		String earlier = Ids.newId();
		Thread.sleep(2);
		String later = Ids.newId();
		assertThat(later).isGreaterThan(earlier);
	}
}
