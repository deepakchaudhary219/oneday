package oneday.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import oneday.config.OneDayProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security. Contact-reaching routes (posting publicly, signalling, revealing, messaging,
 * sparking) require the {@code verified} scope: the progressive-verification gate from blueprint §41.4,
 * enforced in the filter chain so no controller can forget it. Services re-check against the database
 * (see {@code UserGuard}) so a stale token cannot outlive a revoked verification.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	static final String ISSUER = "oneday";

	static final String VERIFIED = "SCOPE_verified";

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/otp/request",
						"/auth/otp/verify")
				.permitAll()
				.requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
						"/swagger-ui.html", "/error")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/moments", "/media/uploads", "/signals", "/signals/*/reveal",
						"/conversations/*/messages", "/connections/*/spark")
				.hasAuthority(VERIFIED)
				.requestMatchers("/staff/members/**", "/staff/audit", "/staff/accounts/**").hasAuthority("SCOPE_admin")
				.requestMatchers("/staff/**").hasAuthority("SCOPE_moderator")
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	SecretKey jwtSigningKey(OneDayProperties properties) {
		return new SecretKeySpec(requireSecret(properties.security().jwtSecret(), "oneday.security.jwt-secret"),
				"HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	static byte[] requireSecret(String secret, String property) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException(property + " must be set to at least 32 bytes");
		}
		return bytes;
	}
}
