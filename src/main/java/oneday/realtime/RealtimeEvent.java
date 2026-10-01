package oneday.realtime;

/**
 * What the app receives on {@code /user/queue/events}: a type and its data. Payloads follow the same
 * visibility rules as the HTTP API (they are built per recipient), and never carry internal user ids.
 *
 * @param type {@code message}, {@code room}, {@code notice} or {@code date-location}
 */
public record RealtimeEvent(String type, Object data) {
}
