package oneday.ledger;

import java.util.List;
import java.util.Objects;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.events.EventMetadata;
import oneday.ledger.LedgerEntry.Kind;
import oneday.profile.Profile;
import oneday.profile.ProfileService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Real Value Ledger from domain events (a CQRS read model). The write side never knows the ledger
 * exists; a new outcome is a new event subscription. Accounts erased before their events arrive are skipped.
 */
@Configuration(proxyBeanMethods = false)
class LedgerProjection {

	private final LedgerRepository ledger;

	private final ProfileService profiles;

	LedgerProjection(LedgerRepository ledger, ProfileService profiles) {
		this.ledger = ledger;
		this.profiles = profiles;
	}

	@Bean
	DomainEventHandler<DomainEvent.MutualRevealed> ledgerOnReveal() {
		return DomainEventHandler.of("ledger.reveal", DomainEvent.MutualRevealed.class, (e, meta) -> {
			record(List.of(e.userA(), e.userB()), Kind.NEW_CONNECTION, meta);
			String regionA = profiles.find(e.userA()).map(Profile::getHomeRegion).orElse(null);
			String regionB = profiles.find(e.userB()).map(Profile::getHomeRegion).orElse(null);
			if (regionA != null && Objects.equals(regionA, regionB)) {
				record(List.of(e.userA(), e.userB()), Kind.ROOTS_CONNECTION, meta);
			}
		});
	}

	@Bean
	DomainEventHandler<DomainEvent.MutualSparked> ledgerOnSpark() {
		return DomainEventHandler.of("ledger.spark", DomainEvent.MutualSparked.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), Kind.MUTUAL_SPARK, meta));
	}

	@Bean
	DomainEventHandler<DomainEvent.DateCompleted> ledgerOnDate() {
		return DomainEventHandler.of("ledger.date", DomainEvent.DateCompleted.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), Kind.DATE_COMPLETED, meta));
	}

	@Bean
	DomainEventHandler<DomainEvent.CoupleFormed> ledgerOnCouple() {
		return DomainEventHandler.of("ledger.couple", DomainEvent.CoupleFormed.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), Kind.COUPLE_FORMED, meta));
	}

	private void record(List<String> userIds, Kind kind, EventMetadata meta) {
		for (String userId : userIds) {
			if (profiles.find(userId).isPresent()
					&& !ledger.existsByUserIdAndKindAndSourceEventId(userId, kind, meta.eventId())) {
				ledger.save(new LedgerEntry(userId, kind, meta.occurredAt(), meta.eventId()));
			}
		}
	}
}
