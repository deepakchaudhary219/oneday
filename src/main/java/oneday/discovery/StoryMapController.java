package oneday.discovery;

import oneday.platform.ReplicaReads;
import oneday.discovery.StoryMapService.StoryMap;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoryMapController {

	private final StoryMapService map;

	public StoryMapController(StoryMapService map) {
		this.map = map;
	}

	/**
	 * Public stories around the caller, in k-anonymous clusters. Lenses: {@code scope} (RADIUS, CITY, ROOTS,
	 * LANGUAGE), {@code activity}, and {@code todaysPrompt=true} for answers to Today's Prompt.
	 */
	@GetMapping("/map/stories")
	StoryMap stories(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "RADIUS") DiscoveryScope scope,
			@RequestParam(required = false) String activity, @RequestParam(defaultValue = "false") boolean todaysPrompt) {
		return ReplicaReads.run(() -> map.map(jwt.getSubject(), scope, activity, todaysPrompt));
	}
}
