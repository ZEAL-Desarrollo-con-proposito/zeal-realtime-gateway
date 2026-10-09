package co.zeal.realtime.websocket;

import co.zeal.realtime.support.Tokens;
import co.zeal.realtime.wellbeing.WellbeingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * De punta a punta sobre SockJS/STOMP real: autenticación del CONNECT, colas privadas, eventos del
 * broker Redis, aislamiento por empresa y señalización WebRTC solo hacia el otro participante.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeGatewayIT {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    WellbeingClient wellbeing;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void notificationEventReachesOnlyItsRecipient() throws Exception {
        StompSession david = connect(Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S."));
        StompSession mariana = connect(Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."));
        BlockingQueue<Map<String, Object>> davidInbox = subscribe(david, "/user/queue/notificaciones");
        BlockingQueue<Map<String, Object>> marianaInbox = subscribe(mariana, "/user/queue/notificaciones");

        publish("zeal.notifications", Map.of("userId", "usr-empleado-001", "type", "RESERVA_CONFIRMADA",
                "title", "Tu cita quedó confirmada", "message", "Laura · lunes", "link", "/empleado/mis-citas"));

        Map<String, Object> event = davidInbox.poll(5, TimeUnit.SECONDS);
        assertThat(event).containsEntry("type", "NUEVA");
        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) event.get("notification");
        assertThat(notification).containsEntry("read", false).containsKeys("id", "createdAt");
        assertThat(marianaInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void freedSlotOfferGoesToEachRecipientPrivately() throws Exception {
        StompSession mariana = connect(Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."));
        BlockingQueue<Map<String, Object>> inbox = subscribe(mariana, "/user/queue/cupos-liberados");

        publish("zeal.wellbeing.freed-slots.offered", Map.of(
                "offer", Map.of("id", "cupo-1", "startsAt", "2026-10-12T14:00:00Z"),
                "recipientIds", List.of("usr-empleado-002", "usr-empleado-004")));

        Map<String, Object> event = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(event).containsEntry("type", "LIBERADO");
        assertThat(event.get("offer")).isEqualTo(Map.of("id", "cupo-1", "startsAt", "2026-10-12T14:00:00Z"));
    }

    @Test
    void organizationTopicsAreClosedToOtherCompanies() throws Exception {
        StompSession outsider = connect(Tokens.token("usr-otra", "EMPLEADO", "Órbita Labs S.A.S."));
        StompSession insider = connect(Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S."));
        String nexuraTopic = Destinations.organizationProjectsTopic("Nexura S.A.S.");
        assertThat(nexuraTopic).isEqualTo("/topic/organizaciones/Nexura%20S.A.S./proyectos");
        BlockingQueue<Map<String, Object>> insiderInbox = subscribe(insider, nexuraTopic);
        subscribe(outsider, nexuraTopic);

        publish("zeal.marketplace.projects.published", Map.of("organization", "Nexura S.A.S.",
                "project", Map.of("id", "proyecto-1"), "detail", Map.of("id", "proyecto-1", "skills", List.of())));

        assertThat(insiderInbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "PUBLICADO");
        Thread.sleep(300);
        assertThat(outsider.isConnected()).as("el servidor cierra la sesión que pidió un tópico ajeno").isFalse();
    }

    @Test
    void webRtcSignalsReachOnlyTheOtherParticipantOfAnActiveRoom() throws Exception {
        String davidToken = Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.");
        StompSession david = connect(davidToken);
        StompSession laura = connect(Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        StompSession intruder = connect(Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."));
        BlockingQueue<Map<String, Object>> lauraInbox = subscribe(laura, "/user/queue/salas");
        BlockingQueue<Map<String, Object>> intruderInbox = subscribe(intruder, "/user/queue/salas");
        when(wellbeing.room(eq(davidToken), eq("reserva-1"))).thenReturn(Optional.of(new WellbeingClient.RoomAccess(
                "reserva-1", "ACTIVA", Instant.now().plus(30, ChronoUnit.MINUTES), "usr-psicologo-001")));
        when(wellbeing.room(any(), eq("reserva-ajena"))).thenReturn(Optional.empty());

        david.send("/app/salas/reserva-1/senales", Map.of("type", "OFERTA", "id", "o-1", "sdp", "v=0"));
        intruder.send("/app/salas/reserva-ajena/senales", Map.of("type", "OFERTA", "id", "x", "sdp", "v=0"));

        Map<String, Object> received = lauraInbox.poll(5, TimeUnit.SECONDS);
        assertThat(received).containsEntry("type", "SENAL").containsEntry("reservationId", "reserva-1");
        assertThat(received.get("signal")).isEqualTo(Map.of("type", "OFERTA", "id", "o-1", "sdp", "v=0"));
        assertThat(lauraInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(intruderInbox.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void chatMessagesWithEncodedConversationIdReachBothParticipants() throws Exception {
        String davidToken = Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.");
        StompSession david = connect(davidToken);
        StompSession laura = connect(Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> lauraInbox = subscribe(laura, "/user/queue/chats");
        BlockingQueue<Map<String, Object>> davidInbox = subscribe(david, "/user/queue/chats");
        String conversationId = "usr-empleado-001:usr-psicologo-001";
        when(wellbeing.conversation(eq(davidToken), eq(conversationId))).thenReturn(Optional.of(
                new WellbeingClient.ConversationAccess(conversationId, "usr-empleado-001", "usr-psicologo-001")));
        when(wellbeing.sendChatMessage(eq(davidToken), eq(conversationId), eq("Hola"))).thenReturn(Optional.of(
                Map.of("id", "mensaje-1", "conversationId", conversationId, "text", "Hola")));

        david.send("/app/chats/usr-empleado-001%3Ausr-psicologo-001/mensajes", Map.of("text", "Hola"));

        assertThat(lauraInbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "NUEVO");
        assertThat(davidInbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "NUEVO");
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
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

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
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
        Thread.sleep(200); // Da tiempo a que el broker registre la suscripción.
        return inbox;
    }

    private void publish(String channel, Object event) throws Exception {
        redis.convertAndSend(channel, objectMapper.writeValueAsString(event));
    }
}
