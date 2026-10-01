package oneday.notify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local development and tests ({@code oneday.push.provider=dev}): logs and records pushes per device. */
@Component
@ConditionalOnProperty(name = "oneday.push.provider", havingValue = "dev")
public class DevPushSender implements PushSender {

	private static final Logger log = LoggerFactory.getLogger(DevPushSender.class);

	private final Map<String, List<PushMessage>> sent = new ConcurrentHashMap<>();

	@Override
	public void send(String pushToken, PushMessage message) {
		sent.computeIfAbsent(pushToken, t -> new CopyOnWriteArrayList<>()).add(message);
		log.info("[dev push] {}: {} / {}", pushToken, message.title(), message.body());
	}

	public List<PushMessage> sentTo(String pushToken) {
		return List.copyOf(sent.getOrDefault(pushToken, List.of()));
	}
}
