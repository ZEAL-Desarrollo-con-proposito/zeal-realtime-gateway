package co.zeal.realtime.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class ChatHistoryClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String marketplaceBaseUrl;

    public ChatHistoryClient(ObjectMapper objectMapper,
                             @Value("${app.marketplace.base-url:http://localhost:8082}") String marketplaceBaseUrl) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.marketplaceBaseUrl = marketplaceBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300))
                .build();
    }

    public ChatMessageResponse persist(String conversationId, String text, String accessToken) {
        String path = "/api/chats/" + UriUtils.encodePathSegment(conversationId, StandardCharsets.UTF_8)
                + "/mensajes";
        try {
            String body = objectMapper.writeValueAsString(Map.of("text", text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(marketplaceBaseUrl + path))
                    .timeout(Duration.ofMillis(400))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ChatPersistenceException(
                        "Marketplace rechazó el mensaje (HTTP " + response.statusCode() + ").");
            }
            ChatMessageResponse saved = objectMapper.readValue(response.body(), ChatMessageResponse.class);
            if (saved.id() == null || saved.conversationId() == null || saved.sentAt() == null) {
                throw new ChatPersistenceException("Marketplace devolvió un mensaje incompleto.");
            }
            return saved;
        } catch (IOException exception) {
            throw new ChatPersistenceException("No se pudo serializar o leer el mensaje del chat.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatPersistenceException("Se interrumpió la persistencia del mensaje.", exception);
        }
    }

    public record ChatMessageResponse(String id, String conversationId, String senderId,
                                      String senderName, String text, Instant sentAt) {}

    public static class ChatPersistenceException extends RuntimeException {
        public ChatPersistenceException(String message) {
            super(message);
        }

        public ChatPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
