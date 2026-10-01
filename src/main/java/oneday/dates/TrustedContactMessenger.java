package oneday.dates;

import oneday.sms.SmsSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Texts trusted contacts. Without an SMS provider the app still works: the person gets the link to share
 * themselves. Message bodies and numbers are never logged here.
 */
@Component
class TrustedContactMessenger {

	private static final Logger log = LoggerFactory.getLogger(TrustedContactMessenger.class);

	private final ObjectProvider<SmsSender> sender;

	TrustedContactMessenger(ObjectProvider<SmsSender> sender) {
		this.sender = sender;
	}

	/** Returns whether the text was handed to a provider. */
	boolean text(String e164Phone, String message) {
		SmsSender sms = sender.getIfAvailable();
		if (sms == null) {
			return false;
		}
		try {
			sms.send(e164Phone, message);
			return true;
		}
		catch (RuntimeException ex) {
			log.warn("Text to a trusted contact failed", ex);
			return false;
		}
	}

	/** Like {@link #text} but failures propagate, so an outbox consumer retries them. */
	void textOrThrow(String e164Phone, String message) {
		SmsSender sms = sender.getIfAvailable();
		if (sms != null) {
			sms.send(e164Phone, message);
		}
	}
}
