package co.zeal.realtime.notification;

import co.zeal.realtime.support.Infrastructure;
import co.zeal.realtime.support.Tokens;
import co.zeal.realtime.wellbeing.WellbeingClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-32 · Telemetría del frontend: validación estricta, límites de tamaño y de frecuencia, métricas acotadas. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.telemetry.requests-per-minute=3")
class TelemetryApiIT {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        Infrastructure.register(registry);
    }

    @LocalServerPort
    int port;
    @Autowired
    MeterRegistry meters;
    @MockitoBean
    WellbeingClient wellbeing;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void validEventsBecomeMetricsWithBoundedLabels() throws Exception {
        String body = """
                {"events":[
                  {"type":"error","name":"chunk_load","route":"/empleado/mis-citas"},
                  {"type":"uso","name":"reservar_cita"},
                  {"type":"uso","name":"nombre_inventado_por_alguien"},
                  {"type":"latencia","name":"notificacion_recibida","valueMs":180}
                ]}""";

        assertThat(post(token(), body).statusCode()).isEqualTo(202);

        assertThat(meters.find("zeal.frontend.errors").tag("name", "chunk_load").counter().count()).isEqualTo(1);
        assertThat(meters.find("zeal.frontend.usage").tag("name", "reservar_cita").counter().count()).isEqualTo(1);
        assertThat(meters.find("zeal.frontend.usage").tag("name", "otro").counter().count())
                .as("nombres fuera de la lista no crean series nuevas").isEqualTo(1);
        assertThat(meters.find("zeal.frontend.latency").tag("name", "notificacion_recibida").timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(180);
    }

    @Test
    void invalidPayloadsAreRejected() throws Exception {
        String token = token();
        assertThat(post(token, "{\"events\":[]}").statusCode()).isEqualTo(400);
        assertThat(post(token, "{\"events\":[{\"type\":\"spam\",\"name\":\"x\"}]}").statusCode()).isEqualTo(400);
        assertThat(post(token, "{\"events\":[{\"type\":\"uso\",\"name\":\"Con Espacios y PII@correo.co\"}]}").statusCode())
                .isEqualTo(400);
        assertThat(post(token, "{\"events\":[{\"type\":\"error\",\"name\":\"error_api\",\"route\":\"/sala/reserva-123\"}]}")
                .statusCode()).as("rutas con identificadores no").isEqualTo(400);
        assertThat(post(token, "{\"events\":[" + "{\"type\":\"uso\",\"name\":\"login\"},".repeat(20)
                + "{\"type\":\"uso\",\"name\":\"login\"}]}").statusCode()).as("más de 20 eventos").isEqualTo(400);
        assertThat(post(token, "{\"events\":[{\"type\":\"uso\",\"name\":\"" + "a".repeat(17_000) + "\"}]}").statusCode())
                .as("cuerpo de más de 16 KB").isEqualTo(413);
        assertThat(post(null, "{\"events\":[{\"type\":\"uso\",\"name\":\"login\"}]}").statusCode()).isEqualTo(401);
    }

    @Test
    void eachPersonHasARateLimitSharedByAllReplicas() throws Exception {
        String token = token();
        String body = "{\"events\":[{\"type\":\"uso\",\"name\":\"login\"}]}";
        for (int i = 0; i < 3; i++) {
            assertThat(post(token, body).statusCode()).isEqualTo(202);
        }
        assertThat(post(token, body).statusCode()).isEqualTo(429);
        assertThat(post(token(), body).statusCode()).as("otra persona no se ve afectada").isEqualTo(202);
    }

    private HttpResponse<String> post(String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/telemetria"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token() {
        return Tokens.token("usr-" + UUID.randomUUID(), "EMPLEADO", "Nexura S.A.S.");
    }
}
