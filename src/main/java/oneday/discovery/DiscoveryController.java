package oneday.discovery;

import oneday.platform.ReplicaReads;
import java.util.List;

import oneday.discovery.DiscoveryViews.Constellation;
import oneday.discovery.DiscoveryViews.HeatCell;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browsing is open right after signup (progressive verification); reaching out requires verification. */
@RestController
@RequestMapping("/discover")
public class DiscoveryController {

	private final DiscoveryService discovery;

	public DiscoveryController(DiscoveryService discovery) {
		this.discovery = discovery;
	}

	@GetMapping("/constellation")
	Constellation constellation(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "RADIUS") DiscoveryScope scope,
			@RequestParam(required = false) String activity, @RequestParam(defaultValue = "0") int page) {
		return ReplicaReads.run(() -> discovery.constellation(jwt.getSubject(), scope, activity, page));
	}

	@GetMapping("/heat")
	List<HeatCell> heat(@AuthenticationPrincipal Jwt jwt) {
		return ReplicaReads.run(() -> discovery.heat(jwt.getSubject()));
	}
}
