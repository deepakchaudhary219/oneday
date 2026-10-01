package oneday.identity;

import jakarta.servlet.http.HttpServletRequest;

/** Who is calling: the network (for abuse limits) and the device, named in the holder's list of sign-ins. */
public record ClientInfo(String ip, String device) {

	public static ClientInfo of(HttpServletRequest request) {
		return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
	}
}
