package co.zeal.realtime.support;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Cliente SockJS/STOMP como el del navegador (latidos de 10 s), para pruebas de punta a punta. */
public final class StompTestClient implements AutoCloseable {

    private final List<StompSession> sessions = new ArrayList<>();
    private final ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();

    public StompTestClient() {
        heartbeats.setPoolSize(2);
        heartbeats.setThreadNamePrefix("test-stomp-heartbeat-");
        heartbeats.initialize();
    }

    public StompSession connect(int port, String token) throws Exception {
        return connect(port, token, 10_000);
    }

    public StompSession connect(int port, String token, long heartbeatMs) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setTaskScheduler(heartbeats);
        client.setDefaultHeartbeat(new long[]{heartbeatMs, heartbeatMs});
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    public static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
            throws InterruptedException {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // Da tiempo a que el broker registre la suscripción.
        return inbox;
    }

    @Override
    public void close() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        heartbeats.shutdown();
    }
}
