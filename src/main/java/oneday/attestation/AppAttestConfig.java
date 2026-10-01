package oneday.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * App Attest settings. The root is Apple's "App Attestation Root CA", published at
 * https://www.apple.com/certificateauthority/Apple_App_Attestation_Root_CA.pem: download it once, check its
 * fingerprint against Apple's PKI page, and point {@code oneday.attestation.apple.root-ca} at the file.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oneday.attestation.apple.enabled", havingValue = "true")
class AppAttestConfig {

	@Bean
	AppAttestVerifier appAttestVerifier(@Value("${oneday.attestation.apple.root-ca}") Resource rootCa,
			@Value("${oneday.attestation.apple.app-id}") String appId,
			@Value("${oneday.attestation.apple.allow-development:false}") boolean allowDevelopment, Clock clock)
			throws IOException, GeneralSecurityException {
		try (InputStream pem = rootCa.getInputStream()) {
			X509Certificate root = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(pem);
			return new AppAttestVerifier(root, appId, allowDevelopment, clock);
		}
	}
}
