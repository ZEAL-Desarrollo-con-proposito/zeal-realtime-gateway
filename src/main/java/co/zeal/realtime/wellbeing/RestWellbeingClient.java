package co.zeal.realtime.wellbeing;

import co.zeal.realtime.shared.web.CorrelationIdFilter;
import org.slf4j.MDC;

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
        this.http = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> { // R2-29: propaga el correlationId
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
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
            // R2-29: nunca el cuerpo (podría repetir el texto del mensaje).
            log.info("El Wellbeing Service rechazó un mensaje de chat ({})", e.getStatusCode());
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("No se pudo entregar un mensaje de chat al Wellbeing Service: {}", e.getClass().getSimpleName());
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
