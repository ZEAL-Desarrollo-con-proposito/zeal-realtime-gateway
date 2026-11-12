package co.zeal.realtime.notification;

import co.zeal.realtime.support.Infrastructure;
import co.zeal.realtime.support.StompTestClient;
import co.zeal.realtime.support.Tokens;
import co.zeal.realtime.wellbeing.WellbeingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static co.zeal.realtime.support.StompTestClient.subscribe;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-06 (RT-2) · API REST de notificaciones del gateway con tokens reales: cada persona ve solo las
 * suyas (de la más nueva a la más vieja), marcarlas como leídas se replica por WebSocket en sus otras
 * pestañas, se guardan como máximo 100 por persona y un token revocado (R2-13) recibe 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.notifications.max-per-user=5")
class NotificationApiIT {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        Infrastructure.register(registry);
    }

    @LocalServerPort
    int port;
    @Autowired
    NotificationService notifications;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    WellbeingClient wellbeing;

    private final StompTestClient clients = new StompTestClient();
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void disconnect() {
        clients.close();
    }

    @Test
    void eachPersonSeesOnlyTheirOwnNotificationsNewestFirst() throws Exception {
        String user = unique("usr");
        notifications.notify(user, "RESERVA_CONFIRMADA", "Primera", "Mensaje 1", "/empleado/mis-citas");
        Thread.sleep(5);
        notifications.notify(user, "RESERVA_CANCELADA", "Segunda", "Mensaje 2", null);
        notifications.notify(unique("otro"), "RESERVA_CONFIRMADA", "Ajena", "No es tuya", null);

        HttpResponse<String> response = call("GET", "/api/notificaciones", token(user));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode list = objectMapper.readTree(response.body());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("title").asText()).isEqualTo("Segunda");
        assertThat(list.get(1).get("link").asText()).isEqualTo("/empleado/mis-citas");
        assertThat(list.get(0).get("read").asBoolean()).isFalse();
    }

    @Test
    void markingAsReadUpdatesTheStoreAndTheOtherTabs() throws Exception {
        String user = unique("usr");
        Notification first = notifications.notify(user, "AVISO", "Uno", "m", null);
        notifications.notify(user, "AVISO", "Dos", "m", null);
        StompSession otherTab = clients.connect(port, token(user));
        BlockingQueue<Map<String, Object>> inbox = subscribe(otherTab, "/user/queue/notificaciones");

        assertThat(call("POST", "/api/notificaciones/" + first.id() + "/leida", token(user)).statusCode()).isEqualTo(204);
        assertThat(inbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "LEIDAS");
        assertThat(call("POST", "/api/notificaciones/leidas", token(user)).statusCode()).isEqualTo(204);
        assertThat(inbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "LEIDAS");

        JsonNode list = objectMapper.readTree(call("GET", "/api/notificaciones", token(user)).body());
        assertThat(list).allMatch(notification -> notification.get("read").asBoolean());
    }

    @Test
    void anotherPersonsNotificationCannotBeTouched() throws Exception {
        Notification foreign = notifications.notify(unique("duena"), "AVISO", "Privada", "m", null);

        HttpResponse<String> response = call("POST", "/api/notificaciones/" + foreign.id() + "/leida", token(unique("intruso")));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("status").asInt()).isEqualTo(404);
    }

    @Test
    void onlyTheMostRecentNotificationsAreKept() throws Exception {
        String user = unique("usr");
        for (int i = 1; i <= 7; i++) {
            notifications.notify(user, "AVISO", "N" + i, "m", null);
            Thread.sleep(2);
        }

        JsonNode list = objectMapper.readTree(call("GET", "/api/notificaciones", token(user)).body());

        assertThat(list).hasSize(5);
        assertThat(list.get(0).get("title").asText()).isEqualTo("N7");
        assertThat(list.get(4).get("title").asText()).isEqualTo("N3");
    }

    @Test
    void revokedOrMissingTokensAreRejected() throws Exception {
        String sessionId = unique("sesion");
        String token = Tokens.token(unique("usr"), "EMPLEADO", "Nexura S.A.S.", sessionId);
        assertThat(call("GET", "/api/notificaciones", token).statusCode()).isEqualTo(200);

        redis.opsForValue().set("zeal:revoked:sid:" + sessionId, "LOGOUT", Duration.ofMinutes(1));

        assertThat(call("GET", "/api/notificaciones", token).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/notificaciones", null).statusCode()).isEqualTo(401);
    }

    @Test
    void unsupportedMethodsAndUnknownRoutesAnswerWithTheApiErrorFormat() throws Exception {
        String token = token(unique("usr"));

        HttpResponse<String> wrongMethod = call("DELETE", "/api/notificaciones", token);
        HttpResponse<String> unknown = call("GET", "/api/no-existe", token);

        assertThat(wrongMethod.statusCode()).isEqualTo(405);
        assertThat(objectMapper.readTree(wrongMethod.body()).get("message").asText()).isNotBlank();
        assertThat(unknown.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> call(String method, String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String userId) {
        return Tokens.token(userId, "EMPLEADO", "Nexura S.A.S.");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
