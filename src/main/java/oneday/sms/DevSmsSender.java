package oneday.sms;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local development and tests only ({@code oneday.sms.provider=dev}): logs the message and keeps the last
 * one per number so a developer or a test can read the code. Never enable outside local environments.
 */
@Component
@ConditionalOnProperty(name = "oneday.sms.provider", havingValue = "dev")
public class DevSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(DevSmsSender.class);

	private final Map<String, String> lastMessage = new ConcurrentHashMap<>();

	@Override
	public void send(String e164Phone, String message) {
		lastMessage.put(e164Phone, message);
		log.info("[dev sms] to {}: {}", e164Phone, message);
	}

	public Optional<String> lastMessageTo(String e164Phone) {
		return Optional.ofNullable(lastMessage.get(e164Phone));
	}
}
