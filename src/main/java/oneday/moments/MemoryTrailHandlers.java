package oneday.moments;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MemoryTrailHandlers {

	/** Copies a kept story's media off the request path; retried by the outbox if storage is unavailable. */
	@Bean
	DomainEventHandler<DomainEvent.MomentKept> copyKeptMedia(MomentService moments) {
		return DomainEventHandler.of("moments.trail-copy", DomainEvent.MomentKept.class,
				(e, meta) -> moments.copyKeptMedia(e.momentId()));
	}
}
