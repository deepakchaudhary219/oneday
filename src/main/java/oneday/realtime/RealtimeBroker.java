package oneday.realtime;

/** Delivers an event to every socket a user holds, on whichever replica it is connected to. */
interface RealtimeBroker {

	void publish(String userId, RealtimeEvent event);
}
