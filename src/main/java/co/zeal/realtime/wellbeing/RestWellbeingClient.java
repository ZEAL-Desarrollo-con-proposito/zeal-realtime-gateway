package co.zeal.realtime.wellbeing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Implementación HTTP del {@link WellbeingClient} con tiempos de espera acotados. */
@Component
public class RestWellbeingClient implements WellbeingClient {

    private static final Logger log = LoggerFactory.getLogger(RestWellbeingClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    public RestWellbeingClient(RestClient.Builder builder, WellbeingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.http = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<RoomAccess> room(String accessToken, String reservationId) {
        return get(accessToken, "/api/wellbeing/reservas/{id}/sala", reservationId).map(room -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> participant = (Map<String, Object>) room.get("participant");
            return new RoomAccess((String) room.get("reservationId"), (String) room.get("status"),
                    Instant.parse((String) room.get("closesAt")), (String) participant.get("id"));
        });
    }

    @Override
    public Optional<ConversationAccess> conversation(String accessToken, String conversationId) {
        return get(accessToken, "/api/chats/{id}", conversationId).map(conversation -> new ConversationAccess(
                (String) conversation.get("id"), (String) conversation.get("employeeId"),
                (String) conversation.get("psychologistId")));
    }

    @Override
    public Optional<Map<String, Object>> sendChatMessage(String accessToken, String conversationId, String text) {
        try {
            return Optional.ofNullable(http.post()
                    .uri("/api/chats/{id}/mensajes", conversationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(JSON_OBJECT));
        } catch (HttpClientErrorException e) {
            log.info("El Wellbeing Service rechazó un mensaje de chat ({}): {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("No se pudo entregar un mensaje de chat al Wellbeing Service", e);
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> get(String accessToken, String path, String id) {
        try {
            return Optional.ofNullable(http.get()
                    .uri(path, id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JSON_OBJECT));
        } catch (HttpClientErrorException e) {
            return Optional.empty(); // 401/403/404: esa persona no participa.
        } catch (RestClientException e) {
            log.warn("No se pudo consultar {} en el Wellbeing Service", path, e);
            return Optional.empty();
        }
    }
}
