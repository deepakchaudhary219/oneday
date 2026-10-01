package oneday.realtime;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Fan-out across replicas. A user's sockets may be on any replica, so with the {@code redis} state store every
 * event is published on one Redis channel and each replica delivers it to the sockets it holds (the simple
 * broker drops it where the user isn't connected). Single-node mode delivers locally.
 */
@Configuration(proxyBeanMethods = false)
class RealtimeBrokers {

	static final String CHANNEL = "oneday:realtime";

	@Bean
	@ConditionalOnProperty(name = "oneday.state.store", havingValue = "memory", matchIfMissing = true)
	RealtimeBroker localRealtimeBroker(SimpMessagingTemplate stomp) {
		return (userId, event) -> stomp.convertAndSendToUser(userId, RealtimeConfig.EVENTS, event);
	}

	@Bean
	@ConditionalOnProperty(name = "oneday.state.store", havingValue = "redis")
	RealtimeBroker redisRealtimeBroker(StringRedisTemplate redis, JsonMapper json) {
		return (userId, event) -> redis.convertAndSend(CHANNEL,
				json.writeValueAsString(Map.of("userId", userId, "type", event.type(), "data", event.data())));
	}

	@Bean
	@ConditionalOnProperty(name = "oneday.state.store", havingValue = "redis")
	RedisMessageListenerContainer realtimeFanOut(RedisConnectionFactory connections, SimpMessagingTemplate stomp,
			JsonMapper json) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connections);
		container.addMessageListener((message, pattern) -> {
			JsonNode node = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
			stomp.convertAndSendToUser(node.get("userId").asText(), RealtimeConfig.EVENTS,
					new RealtimeEvent(node.get("type").asText(), json.treeToValue(node.get("data"), Object.class)));
		}, new ChannelTopic(CHANNEL));
		return container;
	}
}
