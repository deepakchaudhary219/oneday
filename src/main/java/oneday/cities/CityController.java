package oneday.cities;

import java.util.List;

import oneday.cities.CityGates.CityRequest;
import oneday.cities.CityGates.CityView;
import oneday.cities.CityGates.Density;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityController {

	private final CityGates cities;

	public CityController(CityGates cities) {
		this.cities = cities;
	}

	/** The city you're in and what's open there; 204 outside every launched city or when not sharing location. */
	@GetMapping("/cities/current")
	ResponseEntity<CityView> current(@AuthenticationPrincipal Jwt jwt) {
		return cities.current(jwt.getSubject()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@GetMapping("/staff/cities")
	List<CityView> list(@AuthenticationPrincipal Jwt jwt) {
		return cities.list(jwt.getSubject());
	}

	@PutMapping("/staff/cities/{cityId}")
	CityView upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable String cityId, @RequestBody CityRequest request) {
		return cities.upsert(jwt.getSubject(), cityId, request);
	}

	@GetMapping("/staff/cities/{cityId}/density")
	Density density(@AuthenticationPrincipal Jwt jwt, @PathVariable String cityId) {
		return cities.density(jwt.getSubject(), cityId);
	}
}
