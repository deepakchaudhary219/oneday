package oneday.consent;

import java.time.Clock;
import java.util.Optional;

import oneday.common.ApiException;
import oneday.config.OneDayProperties;
import oneday.consent.ConsentRecord.Action;
import oneday.consent.ConsentRecord.Source;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The low-level ledger that feature modules call before processing a purpose's data. It has no dependencies on
 * those modules, so they can depend on it without cycles; withdrawal (which touches them) lives in
 * {@link ConsentService}.
 */
@Component
public class ConsentLedger {

	private final ConsentRecordRepository records;

	private final Clock clock;

	private final String noticeVersion;

	public ConsentLedger(ConsentRecordRepository records, Clock clock, OneDayProperties properties) {
		this.records = records;
		this.clock = clock;
		this.noticeVersion = properties.registration().currentConsentVersion();
	}

	/**
	 * Called at the affirmative action that processes this purpose's data. The first such action after the notice
	 * records consent; after a withdrawal it is refused until the person grants the purpose again.
	 */
	@Transactional
	public void affirm(String userId, ConsentPurpose purpose) {
		Optional<ConsentRecord> latest = latest(userId, purpose);
		if (latest.isPresent() && latest.get().getAction() == Action.WITHDRAWN) {
			throw ApiException.conflict("CONSENT_WITHDRAWN", "You've switched this off in Privacy settings ("
					+ purpose.name().toLowerCase().replace('_', ' ') + "). Turn it back on there first.");
		}
		if (latest.isEmpty()) {
			records.save(new ConsentRecord(userId, purpose, Action.GRANTED, noticeVersion, Source.APP_ACTION,
					clock.instant()));
		}
	}

	@Transactional(readOnly = true)
	public boolean isWithdrawn(String userId, ConsentPurpose purpose) {
		return latest(userId, purpose).map(r -> r.getAction() == Action.WITHDRAWN).orElse(false);
	}

	Optional<ConsentRecord> latest(String userId, ConsentPurpose purpose) {
		return records.findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(userId, purpose);
	}

	ConsentRecord record(String userId, ConsentPurpose purpose, Action action, Source source) {
		return records.save(new ConsentRecord(userId, purpose, action, noticeVersion, source, clock.instant()));
	}

	String noticeVersion() {
		return noticeVersion;
	}
}
