package oneday.rightnow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled off until density Gate 2 (04-implementation-plan.md §5): Right Now in an empty city teaches
 * people the app is empty
 * @param maxResults nearby sessions per response
 */
@ConfigurationProperties("oneday.right-now")
public record RightNowProperties(boolean enabled, int maxResults) {
}
