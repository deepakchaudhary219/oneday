package oneday.consent;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import oneday.consent.ConsentRecord.Action;
import oneday.consent.ConsentRecord.Source;
import oneday.identity.UserGuard;
import oneday.platform.Hashes;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person's consent controls (DPDP s.6): see every purpose with its notice and current state, grant it, or
 * withdraw it "with comparable ease" (s.6(4)). Withdrawal stops the processing and deletes the purpose's data
 * in the same transaction, through each owning module's {@link ConsentWithdrawalEffect}.
 */
@Service
public class ConsentService {

	private final ConsentLedger ledger;

	private final ConsentRecordRepository records;

	private final List<ConsentWithdrawalEffect> effects;

	private final UserGuard guard;

	public ConsentService(ConsentLedger ledger, ConsentRecordRepository records, List<ConsentWithdrawalEffect> effects,
			UserGuard guard) {
		this.ledger = ledger;
		this.records = records;
		this.effects = List.copyOf(effects);
		this.guard = guard;
	}

	@Transactional(readOnly = true)
	public List<PurposeView> purposes(String userId) {
		guard.requireExisting(userId);
		return Arrays.stream(ConsentPurpose.values()).map(p -> {
			var latest = ledger.latest(userId, p);
			String state = latest.map(r -> r.getAction().name()).orElse("NOT_GIVEN");
			return new PurposeView(p, p.processes(), p.onWithdrawal(), state,
					latest.map(ConsentRecord::getCreatedAt).orElse(null),
					latest.map(ConsentRecord::getNoticeVersion).orElse(ledger.noticeVersion()));
		}).toList();
	}

	@Transactional
	public List<PurposeView> grant(String userId, ConsentPurpose purpose) {
		guard.requireExisting(userId);
		if (ledger.latest(userId, purpose).map(r -> r.getAction() != Action.GRANTED).orElse(true)) {
			ledger.record(userId, purpose, Action.GRANTED, Source.SETTINGS);
		}
		return purposes(userId);
	}

	/** Works for suspended accounts too: data rights don't depend on account standing. */
	@Transactional
	public List<PurposeView> withdraw(String userId, ConsentPurpose purpose) {
		guard.requireExisting(userId);
		if (!ledger.isWithdrawn(userId, purpose)) {
			ledger.record(userId, purpose, Action.WITHDRAWN, Source.SETTINGS);
			effects.stream().filter(e -> e.purpose() == purpose).forEach(e -> e.withdraw(userId));
		}
		return purposes(userId);
	}

	@Transactional(readOnly = true)
	public List<HistoryItem> history(String userId) {
		guard.requireExisting(userId);
		return records.findByUserIdOrderByCreatedAtAscIdAsc(userId)
			.stream()
			.map(r -> new HistoryItem(r.getPurpose(), r.getAction(), r.getSource(), r.getNoticeVersion(), r.getCreatedAt()))
			.toList();
	}

	/** Erasure: the proof of consent is kept (s.6(10)) but no longer names the person. */
	@Transactional
	public void forget(String userId) {
		String ref = "erased:" + Hashes.sha256(userId).substring(0, 32);
		records.findByUserIdOrderByCreatedAtAscIdAsc(userId).forEach(r -> r.pseudonymise(ref));
	}

	/**
	 * @param state {@code GRANTED}, {@code WITHDRAWN} or {@code NOT_GIVEN}
	 */
	public record PurposeView(ConsentPurpose purpose, String processes, String onWithdrawal, String state,
			Instant since, String noticeVersion) {
	}

	public record HistoryItem(ConsentPurpose purpose, Action action, Source source, String noticeVersion, Instant at) {
	}
}
