package oneday.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells a user's open apps that something happened. Sent only after the surrounding transaction commits (a
 * rolled-back message is never announced), and best-effort: the HTTP API remains the source of truth, so a
 * missed event just means the app picks the change up on its next fetch.
 */
@Service
public class RealtimeService {

	private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

	private final RealtimeBroker broker;

	RealtimeService(RealtimeBroker broker) {
		this.broker = broker;
	}

	public void toUser(String userId, String type, Object data) {
		RealtimeEvent event = new RealtimeEvent(type, data);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void afterCommit() {
					send(userId, event);
				}
			});
		}
		else {
			send(userId, event);
		}
	}

	private void send(String userId, RealtimeEvent event) {
		try {
			broker.publish(userId, event);
		}
		catch (RuntimeException ex) {
			log.warn("Realtime delivery failed (the app will catch up on its next fetch)", ex);
		}
	}
}
