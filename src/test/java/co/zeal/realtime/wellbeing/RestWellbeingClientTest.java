package co.zeal.realtime.wellbeing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El gateway pregunta al Wellbeing Service, con el token de cada persona, si participa en una sala o
 * conversación. Contra un servidor HTTP real: 200 → acceso, 4xx → sin acceso, 5xx/lentitud → sin acceso
 * y sin colgar el hilo (tiempo de espera acotado).
 */
class RestWellbeingClientTest {

    private HttpServer server;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private RestWellbeingClient client;

    @BeforeEach
    void startFakeWellbeing() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/api/wellbeing/reservas/reserva-1/sala", 200, """
                {"reservationId":"reserva-1","status":"ACTIVA","closesAt":"2026-11-12T15:00:00Z",
                 "participant":{"id":"usr-psicologo-001","fullName":"Laura"}}""");
        respond("/api/wellbeing/reservas/ajena/sala", 404, "{\"status\":404}");
        respond("/api/wellbeing/reservas/caida/sala", 503, "{}");
        respond("/api/chats/c-1", 200, "{\"id\":\"c-1\",\"employeeId\":\"e\",\"psychologistId\":\"p\"}");
        respond("/api/chats/c-1/mensajes", 201, "{\"id\":\"m-1\",\"text\":\"Hola\"}");
        respond("/api/chats/bloqueada/mensajes", 422, "{\"message\":\"No puedes escribir aquí\"}");
        server.createContext("/api/chats/lenta", exchange -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        WellbeingProperties properties = new WellbeingProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofMillis(400), Duration.ofSeconds(30));
        client = new RestWellbeingClient(RestClient.builder(), properties);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void roomOfAParticipantIsReadWithTheirToken() {
        var room = client.room("token-de-david", "reserva-1");

        assertThat(room).contains(new WellbeingClient.RoomAccess("reserva-1", "ACTIVA",
                Instant.parse("2026-11-12T15:00:00Z"), "usr-psicologo-001"));
        assertThat(authorizations).containsExactly("Bearer token-de-david");
    }

    @Test
    void foreignOrUnavailableRoomsGiveNoAccess() {
        assertThat(client.room("t", "ajena")).isEmpty();
        assertThat(client.room("t", "caida")).isEmpty();
    }

    @Test
    void conversationAndMessages() {
        assertThat(client.conversation("t", "c-1")).contains(new WellbeingClient.ConversationAccess("c-1", "e", "p"));
        assertThat(client.sendChatMessage("t", "c-1", "Hola")).contains(Map.of("id", "m-1", "text", "Hola"));
        assertThat(client.sendChatMessage("t", "bloqueada", "Hola")).isEmpty();
    }

    @Test
    void aSlowWellbeingServiceDoesNotHangTheGateway() {
        long start = System.nanoTime();

        assertThat(client.conversation("t", "lenta")).isEmpty();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_200));
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
