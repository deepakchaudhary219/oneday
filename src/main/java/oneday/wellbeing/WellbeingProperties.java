package oneday.wellbeing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param askPercent share of person-days on which the question may be asked (2 = about 1 in 50)
 * @param minGap never ask the same person more often than this
 * @param minAnswersToReport a week's well-spent share is reported only with at least this many answers
 */
@ConfigurationProperties("oneday.wellbeing")
public record WellbeingProperties(int askPercent, Duration minGap, int minAnswersToReport) {
}
