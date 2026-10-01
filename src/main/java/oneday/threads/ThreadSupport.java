package oneday.threads;

import oneday.events.DomainEvent;
import oneday.events.DomainEventHandler;
import oneday.safety.SafetyTargetResolver;
import oneday.safety.SafetyTargets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ThreadSupport {

	@Bean
	DomainEventHandler<DomainEvent.ThreadPostAdded> preserveThreadMedia(ThreadService threads) {
		return DomainEventHandler.of("threads.preserve-media", DomainEvent.ThreadPostAdded.class,
				(e, meta) -> threads.preserveMedia(e.postId()));
	}

	@Bean
	SafetyTargetResolver threadPostTarget(ThreadService threads) {
		return SafetyTargets.of("threadPostId", "THREAD_POST", threads::authorOf);
	}
}
