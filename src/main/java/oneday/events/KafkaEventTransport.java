package oneday.events;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import tools.jackson.databind.json.JsonMapper;

/**
 * The broker transport (tech arch v2 §1.3, "add Kafka when ≥ 3 consumers need the same events"), switched on
 * with {@code oneday.events.transport=kafka}. Nothing upstream changes: modules still publish into the outbox
 * in their own transaction, and the relay now hands each event to Kafka and marks it dispatched only after
 * the broker acknowledged it (acks=all, idempotent producer).
 *
 * <ul>
 * <li>The record key is the aggregate id, so all events about one entity land on one partition, in order.</li>
 * <li>Consumers run as a consumer group (scale out by adding replicas, up to the partition count) and call
 * the same idempotent handlers ({@link EventDispatcher}), so redelivery is harmless.</li>
 * <li>A failing record is retried with exponential backoff, then parked on {@code <topic>.DLT}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oneday.events.transport", havingValue = "kafka")
class KafkaEventTransport {

	private static final Logger log = LoggerFactory.getLogger(KafkaEventTransport.class);

	/** What travels on the topic: the catalogue payload plus the envelope the handlers need. */
	record Envelope(String id, String type, int schemaVersion, Instant occurredAt, int attempt, String payload) {
	}

	@Bean
	NewTopic domainEventsTopic(@Value("${oneday.events.kafka.topic}") String topic,
			@Value("${oneday.events.kafka.partitions}") int partitions,
			@Value("${oneday.events.kafka.replicas}") short replicas) {
		return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
	}

	@Bean
	NewTopic domainEventsDeadLetterTopic(@Value("${oneday.events.kafka.topic}") String topic,
			@Value("${oneday.events.kafka.partitions}") int partitions,
			@Value("${oneday.events.kafka.replicas}") short replicas) {
		return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
	}

	@Bean
	EventTransport kafkaTransport(KafkaTemplate<String, String> kafka, EventPublisher codec, JsonMapper json,
			@Value("${oneday.events.kafka.topic}") String topic) {
		return (event, metadata) -> {
			Envelope envelope = new Envelope(metadata.eventId(), event.type(), DomainEvent.SCHEMA_VERSION,
					metadata.occurredAt(), metadata.attempt(), codec.encode(event));
			try {
				kafka.send(topic, event.aggregateId(), json.writeValueAsString(envelope)).get(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while publishing " + event.type(), ex);
			}
			catch (Exception ex) {
				throw new IllegalStateException("Kafka did not acknowledge " + event.type(), ex);
			}
		};
	}

	@Bean
	CommonErrorHandler eventErrorHandler(KafkaTemplate<String, String> kafka) {
		ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
		backOff.setMaxElapsedTime(30_000);
		return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backOff);
	}

	@Bean
	EventConsumer domainEventConsumer(EventDispatcher dispatcher, EventPublisher codec, JsonMapper json) {
		return new EventConsumer(dispatcher, codec, json);
	}

	/** One member of the {@code oneday.events.kafka.group} consumer group. */
	static class EventConsumer {

		private final EventDispatcher dispatcher;

		private final EventPublisher codec;

		private final JsonMapper json;

		EventConsumer(EventDispatcher dispatcher, EventPublisher codec, JsonMapper json) {
			this.dispatcher = dispatcher;
			this.codec = codec;
			this.json = json;
		}

		@KafkaListener(topics = "${oneday.events.kafka.topic}", groupId = "${oneday.events.kafka.group}",
				concurrency = "${oneday.events.kafka.concurrency}")
		public void on(ConsumerRecord<String, String> record) {
			Envelope envelope = json.readValue(record.value(), Envelope.class);
			if (envelope.schemaVersion() > DomainEvent.SCHEMA_VERSION) {
				// A newer producer during a rolling deploy: fail so it is retried, then dead-lettered for review.
				throw new IllegalStateException("Unsupported event schema " + envelope.schemaVersion());
			}
			DomainEvent event = codec.decode(envelope.type(), envelope.payload());
			dispatcher.dispatch(event, new EventMetadata(envelope.id(), envelope.occurredAt(), envelope.attempt()));
			log.debug("Handled {} {} from partition {}", envelope.type(), envelope.id(), record.partition());
		}
	}
}
