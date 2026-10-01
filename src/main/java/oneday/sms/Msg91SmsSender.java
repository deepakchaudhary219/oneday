package oneday.sms;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SMS through MSG91's flow API ({@code oneday.sms.provider=msg91}). Each {@link SmsTemplate} maps to a template
 * registered with DLT; the message is sent as that template id plus its variables, never as free text.
 * Numbers go out without the leading '+'. Non-2xx answers throw, so outbox-driven texts are retried; a
 * template that isn't configured fails fast at send time with a clear error.
 */
@Component
@ConditionalOnProperty(name = "oneday.sms.provider", havingValue = "msg91")
public class Msg91SmsSender implements SmsSender {

	private final Msg91Properties settings;

	private final HttpClient http;

	private final JsonMapper json;

	public Msg91SmsSender(Msg91Properties settings, HttpClient vendorHttpClient, JsonMapper json) {
		if (settings.authKey() == null || settings.authKey().isBlank()) {
			throw new IllegalStateException("oneday.sms.msg91.auth-key must be set for the msg91 provider");
		}
		this.settings = settings;
		this.http = vendorHttpClient;
		this.json = json;
	}

	/** Free text is not allowed on DLT routes: only templates may be sent. */
	@Override
	public void send(String e164Phone, String message) {
		throw new UnsupportedOperationException("DLT requires a registered template; use send(phone, template, ...)");
	}

	@Override
	public void send(String e164Phone, SmsTemplate template, Map<String, String> variables, String text) {
		String templateId = settings.templates() == null ? null : settings.templates().get(template.name());
		if (templateId == null || templateId.isBlank()) {
			throw new IllegalStateException("No MSG91 template configured for " + template);
		}
		Map<String, Object> recipient = new LinkedHashMap<>();
		recipient.put("mobiles", e164Phone.startsWith("+") ? e164Phone.substring(1) : e164Phone);
		recipient.putAll(variables);
		Map<String, Object> body = Map.of("template_id", templateId, "short_url", "0", "recipients", List.of(recipient));
		String base = settings.endpoint() == null ? "https://control.msg91.com" : settings.endpoint();
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v5/flow"))
			.timeout(Duration.ofSeconds(10))
			.header("authkey", settings.authKey())
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
			.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2 || (response.body() != null && response.body().contains("\"error\""))) {
				throw new IllegalStateException("MSG91 refused the message (" + response.statusCode() + ")");
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("MSG91 unreachable", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", ex);
		}
	}
}
