package oneday.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * With the {@code redis} state store, session ends are published on a Redis channel and every replica evicts
 * them from its local cache: cache-aside with pub/sub invalidation.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oneday.state.store", havingValue = "redis")
class RedisSessionEvictions {

	static final String CHANNEL = "oneday:session-ended";

	@Bean
	SessionEvictions sessionEvictions(StringRedisTemplate redis) {
		return new SessionEvictions() {

			@Override
			public void sessionEnded(String sessionId) {
				redis.convertAndSend(CHANNEL, "s:" + sessionId);
			}

			@Override
			public void userSessionsEnded(String userId) {
				redis.convertAndSend(CHANNEL, "u:" + userId);
			}
		};
	}

	@Bean
	RedisMessageListenerContainer sessionEvictionListener(RedisConnectionFactory connections,
			@Lazy SessionLivenessCache cache) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connections);
		container.addMessageListener((message, pattern) -> {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			if (body.startsWith("s:")) {
				cache.evictLocally(body.substring(2));
			}
			else if (body.startsWith("u:")) {
				cache.evictUserLocally(body.substring(2));
			}
		}, new ChannelTopic(CHANNEL));
		return container;
	}
}
