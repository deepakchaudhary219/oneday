package oneday.identity;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 10, max = 128) String password,
		@NotNull LocalDate dateOfBirth,
		@NotBlank @Size(max = 40) String displayName,
		@NotBlank @Size(max = 32) String consentVersion) {
}
