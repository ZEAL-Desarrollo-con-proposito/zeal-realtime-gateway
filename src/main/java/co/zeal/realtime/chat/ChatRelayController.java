package co.zeal.realtime.chat;

import co.zeal.realtime.metrics.DeliveryLatency;

import co.zeal.realtime.wellbeing.ParticipantAccess;
import co.zeal.realtime.wellbeing.WellbeingClient;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import co.zeal.realtime.websocket.StompPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Reto RT-1: chat privado y aviso de "escribiendo". El mensaje se guarda primero en el Wellbeing
 * Service (dueño del contenido clínico) y luego se entrega a los dos participantes, incluido el eco
 * a las otras pestañas de quien lo envió.
 */
@Controller
public class ChatRelayController {

    private final WellbeingClient wellbeing;
    private final ParticipantAccess access;
    private final RealtimeSender sender;
    private final DeliveryLatency latency;

    public ChatRelayController(WellbeingClient wellbeing, ParticipantAccess access, RealtimeSender sender,
                               DeliveryLatency latency) {
        this.latency = latency;
        this.wellbeing = wellbeing;
        this.access = access;
        this.sender = sender;
    }

    @MessageMapping("/chats/{conversationId}/mensajes")
    public void relayMessage(@DestinationVariable("conversationId") String encodedConversationId,
                             @Payload ChatOutgoing outgoing, StompPrincipal principal) {
        String conversationId = Destinations.decode(encodedConversationId);
        if (outgoing.text() == null || outgoing.text().isBlank()) {
            return;
        }
        access.conversation(principal, conversationId).ifPresent(conversation ->
                wellbeing.sendChatMessage(principal.accessToken(), conversationId, outgoing.text())
                        .ifPresent(message -> {
                            sender.toUsers(List.of(conversation.employeeId(), conversation.psychologistId()),
                                    Destinations.CHATS_QUEUE, Map.of("type", "NUEVO", "message", message));
                            latency.record("chat", String.valueOf(message.get("sentAt")));
                        }));
    }

    /** Solo a la otra persona: quien escribe ya sabe que está escribiendo. */
    @MessageMapping("/chats/{conversationId}/escribiendo")
    public void relayTyping(@DestinationVariable("conversationId") String encodedConversationId,
                            @Payload ChatTypingOutgoing typing, StompPrincipal principal) {
        String conversationId = Destinations.decode(encodedConversationId);
        access.conversation(principal, conversationId).ifPresent(conversation -> sender.toUser(
                conversation.otherThan(principal.getName()), Destinations.CHATS_QUEUE,
                Map.of("type", "ESCRIBIENDO", "conversationId", conversation.id(), "userId", principal.getName(),
                        "typing", typing.typing())));
    }

    public record ChatOutgoing(String text) {
    }

    public record ChatTypingOutgoing(boolean typing) {
    }
}
