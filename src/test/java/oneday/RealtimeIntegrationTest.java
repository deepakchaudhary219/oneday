package oneday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import oneday.realtime.RealtimeSessions;
import oneday.support.ApiTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Real-time delivery over a real WebSocket: authenticated with the access token, receive-only, each side gets
 * its own view, and a signed-out session's socket is closed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest extends ApiTestSupport {

	@LocalServerPort
	int port;

	@Autowired
	RealtimeSessions sockets;

	private final WebSocketStompClient client = stompClient();

	@AfterEach
	void stop() {
		client.stop();
	}

	@Test
	void chatMessagesArriveLiveForBothPeople() throws Exception {
		String asha = verifiedUser("Asha");
		String ravi = verifiedUser("Ravi");
		connect(asha, ravi);
		String conversation = JsonPath.read(body(getAs(asha, "/connections")), "$[0].conversationId");

		BlockingQueue<String> raviEvents = new LinkedBlockingQueue<>();
		BlockingQueue<String> ashaEvents = new LinkedBlockingQueue<>();
		subscribe(ravi, raviEvents);
		subscribe(asha, ashaEvents);

		postAs(asha, "/conversations/" + conversation + "/messages", "{\"body\":\"Chai at 6?\"}")
			.andExpect(status().isCreated());
		String toRavi = raviEvents.poll(10, TimeUnit.SECONDS);
		assertThat(toRavi).isNotNull();
		assertThat((String) JsonPath.read(toRavi, "$.type")).isEqualTo("message");
		assertThat((String) JsonPath.read(toRavi, "$.data.conversationId")).isEqualTo(conversation);
		assertThat((String) JsonPath.read(toRavi, "$.data.message.body")).isEqualTo("Chai at 6?");
		assertThat((Boolean) JsonPath.read(toRavi, "$.data.message.mine")).isFalse();
		assertThat(toRavi).doesNotContain(userIdOf(asha));
		String toAsha = ashaEvents.poll(10, TimeUnit.SECONDS);
		assertThat((Boolean) JsonPath.read(toAsha, "$.data.message.mine")).isTrue(); // her other devices sync
	}

	@Test
	void socketsNeedAValidTokenAreReceiveOnlyAndCloseWhenTheSessionEnds() throws Exception {
		assertThatThrownBy(() -> session("not-a-token")).isInstanceOf(ExecutionException.class);

		String asha = register("Asha");
		StompSession socket = session(asha);
		BlockingQueue<String> none = new LinkedBlockingQueue<>();
		// Subscribing to anyone else's queue (or any other destination) is refused, which closes the socket.
		socket.subscribe("/queue/events", handler(none));
		Thread.sleep(500);
		assertThat(socket.isConnected()).isFalse();

		StompSession good = session(asha);
		good.subscribe("/user/queue/events", handler(none));
		postAs(asha, "/auth/logout", null).andExpect(status().isNoContent());
		assertThat(sockets.sweep()).isGreaterThanOrEqualTo(1);
		Thread.sleep(500);
		assertThat(good.isConnected()).isFalse();
	}

	private void subscribe(String token, BlockingQueue<String> into) throws Exception {
		session(token).subscribe("/user/queue/events", handler(into));
		Thread.sleep(300); // let the subscription register before events are sent
	}

	private StompSession session(String token) throws Exception {
		StompHeaders connect = new StompHeaders();
		connect.add("Authorization", "Bearer " + token);
		return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
				new StompSessionHandlerAdapter() {
				})
			.get(10, TimeUnit.SECONDS);
	}

	private static StompFrameHandler handler(BlockingQueue<String> into) {
		return new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return byte[].class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				into.add(new String((byte[]) payload, StandardCharsets.UTF_8));
			}
		};
	}

	private static WebSocketStompClient stompClient() {
		WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
		stomp.setMessageConverter(new ByteArrayMessageConverter() {

			@Override
			protected boolean supportsMimeType(MessageHeaders headers) {
				return true;
			}
		});
		return stomp;
	}
}
