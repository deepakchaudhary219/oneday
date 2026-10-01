package oneday.grievance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Grievance Officer's published contact (IT Rules 3(2)(a), DPDP Rules: shown in the app and on the
 * website) and how many grievances one account may file per day.
 */
@ConfigurationProperties("oneday.grievance")
public record GrievanceProperties(String officerName, String officerEmail, String officerAddress, int perUserPerDay) {
}
