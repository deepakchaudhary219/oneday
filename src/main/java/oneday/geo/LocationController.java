package oneday.geo;

import oneday.attestation.AttestationGuard;

import oneday.geo.LocationService.LocationView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/location")
public class LocationController {

	private final LocationService locations;

	private final AttestationGuard attestation;

	public LocationController(LocationService locations, AttestationGuard attestation) {
		this.locations = locations;
		this.attestation = attestation;
	}

	@GetMapping
	ResponseEntity<LocationView> current(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.of(locations.current(jwt.getSubject()));
	}

	/** Foreground ping. Raw coordinates are snapped to a cell immediately and never stored or logged. */
	@PutMapping
	LocationView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LocationUpdate update,
			@RequestHeader(name = AttestationGuard.HEADER, required = false) String integrity) {
		attestation.check(integrity, "location");
		return locations.update(jwt.getSubject(), update.lat(), update.lon());
	}

	@PostMapping("/pause")
	ResponseEntity<Void> pause(@AuthenticationPrincipal Jwt jwt) {
		locations.pause(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/safe-zone")
	LocationView markSafeZone(@AuthenticationPrincipal Jwt jwt) {
		return locations.markSafeZoneHere(jwt.getSubject());
	}

	@DeleteMapping("/safe-zone")
	ResponseEntity<Void> clearSafeZone(@AuthenticationPrincipal Jwt jwt) {
		locations.clearSafeZone(jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	record LocationUpdate(
			@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
			@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon) {
	}
}
