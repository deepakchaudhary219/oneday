package oneday.sms;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MSG91 (a TRAI DLT-compliant Indian SMS gateway).
 *
 * @param authKey the account's API key
 * @param endpoint API base (overridable for tests)
 * @param templates MSG91 flow/template id per {@link SmsTemplate} name, each registered on the DLT platform
 */
@ConfigurationProperties("oneday.sms.msg91")
public record Msg91Properties(String authKey, String endpoint, Map<String, String> templates) {
}
