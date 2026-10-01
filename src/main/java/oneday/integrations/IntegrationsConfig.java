package oneday.integrations;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared clients for vendor adapters. */
@Configuration(proxyBeanMethods = false)
public class IntegrationsConfig {

	/** One pooled HTTP/2-capable client for every vendor call, with a short connect timeout. */
	@Bean
	HttpClient vendorHttpClient() {
		return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	@Bean
	@ConditionalOnProperty(name = "oneday.google.credentials-file")
	GoogleServiceAccount googleServiceAccount(
			@Value("${oneday.google.credentials-file}") String file,
			HttpClient vendorHttpClient, JsonMapper json, Clock clock) {
		return new GoogleServiceAccount(Path.of(file), vendorHttpClient, json, clock);
	}
}
