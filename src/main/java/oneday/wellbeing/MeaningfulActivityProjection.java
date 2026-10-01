package oneday.wellbeing;

import java.util.List;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.events.EventMetadata;
import oneday.profile.ProfileService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feeds Weekly Meaningful Actives from domain events: only two-way moments count (a one-way signal or a
 * post on its own does not). Erased accounts are skipped.
 */
@Configuration(proxyBeanMethods = false)
class MeaningfulActivityProjection {

	private final WellbeingService wellbeing;

	private final ProfileService profiles;

	MeaningfulActivityProjection(WellbeingService wellbeing, ProfileService profiles) {
		this.wellbeing = wellbeing;
		this.profiles = profiles;
	}

	@Bean
	DomainEventHandler<DomainEvent.MutualRevealed> wmaOnReveal() {
		return DomainEventHandler.of("wma.reveal", DomainEvent.MutualRevealed.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), meta));
	}

	@Bean
	DomainEventHandler<DomainEvent.MutualSparked> wmaOnSpark() {
		return DomainEventHandler.of("wma.spark", DomainEvent.MutualSparked.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), meta));
	}

	@Bean
	DomainEventHandler<DomainEvent.CoupleFormed> wmaOnCouple() {
		return DomainEventHandler.of("wma.couple", DomainEvent.CoupleFormed.class,
				(e, meta) -> record(List.of(e.userA(), e.userB()), meta));
	}

	/** Answering someone's story is a two-way act for the person who answered. */
	@Bean
	DomainEventHandler<DomainEvent.RelayJoined> wmaOnRelay() {
		return DomainEventHandler.of("wma.relay", DomainEvent.RelayJoined.class,
				(e, meta) -> record(List.of(e.joinerId()), meta));
	}

	@Bean
	DomainEventHandler<DomainEvent.DateConfirmed> wmaOnDate() {
		return DomainEventHandler.of("wma.date", DomainEvent.DateConfirmed.class,
				(e, meta) -> record(List.of(e.proposerId(), e.partnerId()), meta));
	}

	private void record(List<String> userIds, EventMetadata meta) {
		userIds.stream()
			.filter(id -> profiles.find(id).isPresent())
			.forEach(id -> wellbeing.recordMeaningful(id, meta.occurredAt()));
	}
}
