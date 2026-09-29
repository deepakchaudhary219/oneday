package oneday.safety;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only block lookups used by every module. Blocks are always enforced in both directions: neither
 * person can discover, signal, reveal, spark or message the other.
 */
@Component
public class BlockChecker {

	private final BlockRepository blocks;

	public BlockChecker(BlockRepository blocks) {
		this.blocks = blocks;
	}

	@Transactional(readOnly = true)
	public boolean isBlockedEitherWay(String a, String b) {
		return blocks.existsEitherWay(a, b);
	}

	/** Everyone this user blocked or was blocked by. */
	@Transactional(readOnly = true)
	public Set<String> blockedEitherWay(String userId) {
		Set<String> result = new HashSet<>();
		blocks.findByBlockerIdOrBlockedId(userId, userId).forEach(b -> {
			result.add(b.getBlockerId().equals(userId) ? b.getBlockedId() : b.getBlockerId());
		});
		return result;
	}
}
