package co.zeal.realtime.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

@Controller
public class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

    private final ChatHistoryClient chatHistoryClient;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageController(ChatHistoryClient chatHistoryClient,
                                 SimpMessagingTemplate messagingTemplate) {
        this.chatHistoryClient = chatHistoryClient;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chats/{conversationId}/mensajes")
    public void send(@DestinationVariable String conversationId,
                     @Payload OutgoingChatMessage outgoing,
                     Principal principal) {
        if (!(principal instanceof RealtimePrincipal sender)) {
            throw new IllegalArgumentException("El emisor no está autenticado.");
        }
        String decodedConversationId = UriUtils.decode(conversationId, StandardCharsets.UTF_8);
        String[] participants = decodedConversationId.split(":", -1);
        if (participants.length != 2 || participants[0].isBlank() || participants[1].isBlank()) {
            throw new IllegalArgumentException("Identificador de conversación inválido.");
        }
        String peerId;
        if (isEmployee(sender.role()) && sender.userId().equals(participants[0])) {
            peerId = participants[1];
        } else if (isPsychologist(sender.role()) && sender.userId().equals(participants[1])) {
            peerId = participants[0];
        } else {
            throw new IllegalArgumentException("El emisor no pertenece a esta conversación.");
        }
        if (outgoing == null || outgoing.text() == null || outgoing.text().isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        long startedAt = System.nanoTime();
        ChatHistoryClient.ChatMessageResponse saved = chatHistoryClient.persist(
                decodedConversationId, outgoing.text(), sender.accessToken());
        Map<String, Object> event = Map.of("type", "NUEVO", "message", saved);
        messagingTemplate.convertAndSendToUser(sender.userId(), "/queue/chats", event);
        messagingTemplate.convertAndSendToUser(peerId, "/queue/chats", event);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        if (elapsedMillis >= 500) {
            log.warn("Entrega de chat superó el objetivo de latencia: {} ms, conversación {}",
                    elapsedMillis, decodedConversationId);
        } else {
            log.debug("Entrega de chat completada en {} ms, conversación {}", elapsedMillis, decodedConversationId);
        }
    }

    private boolean isEmployee(String role) {
        return "EMPLEADO".equals(role) || "EMPLOYEE".equals(role);
    }

    private boolean isPsychologist(String role) {
        return "PSICOLOGO".equals(role) || "PSYCHOLOGIST".equals(role);
    }

    public record OutgoingChatMessage(String text) {}
}
