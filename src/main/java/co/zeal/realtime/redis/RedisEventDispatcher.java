package co.zeal.realtime.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RedisEventDispatcher implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventDispatcher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisEventDispatcher(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        log.debug("[EDA] Canal: {} | Payload: {}", channel, body);
        try {
            dispatch(channel, body);
        } catch (IOException e) {
            log.error("[EDA] Error procesando evento en canal {}: {}", channel, e.getMessage());
        }
    }

    private void dispatch(String channel, String body) throws IOException {
        switch (channel) {

            // RT-2: Cambios de franjas → broadcast a todos los clientes
            case "events.slots" ->
                messagingTemplate.convertAndSend("/topic/franjas", body);

            // RT-2: Cambios de cupos en proyectos/talleres → broadcast
            case "events.projects", "events.projects.capacity" ->
                messagingTemplate.convertAndSend("/topic/proyectos", body);

            // RT-2: Notificación privada por usuario
            case "events.notifications" -> {
                JsonNode root = objectMapper.readTree(body);
                String userId = root.get("userId").asText();
                String payload = objectMapper.writeValueAsString(
                        Map.of("type", "NUEVA", "notification",
                               objectMapper.treeToValue(root.get("notification"), Object.class)));
                messagingTemplate.convertAndSendToUser(userId, "/queue/notificaciones", payload);
            }

            // C-3 + RT-2: Alerta de cupo liberado → envío individual a cada candidato
            case "events.freed_slots" -> {
                JsonNode root = objectMapper.readTree(body);
                JsonNode offerNode = root.get("offer");
                JsonNode candidatesNode = root.get("candidateIds");
                List<String> candidateIds = new ArrayList<>();
                for (JsonNode node : candidatesNode) {
                    candidateIds.add(node.asText());
                }
                String payload = objectMapper.writeValueAsString(
                        Map.of("type", "LIBERADO", "offer",
                               objectMapper.treeToValue(offerNode, Object.class)));
                for (String candidateId : candidateIds) {
                    messagingTemplate.convertAndSendToUser(candidateId, "/queue/cupos-liberados", payload);
                }
            }

            // C-3: Cupo tomado → notificar a los perdedores
            case "events.freed_slots_taken" -> {
                JsonNode root = objectMapper.readTree(body);
                String offerId = root.get("offerId").asText();
                String winnerId = root.get("winnerId").asText();
                JsonNode allCandidates = root.get("allCandidateIds");
                String takenPayload = objectMapper.writeValueAsString(
                        Map.of("type", "TOMADO", "offerId", offerId));
                for (JsonNode node : allCandidates) {
                    String candidateId = node.asText();
                    if (!candidateId.equals(winnerId)) {
                        messagingTemplate.convertAndSendToUser(candidateId, "/queue/cupos-liberados", takenPayload);
                    }
                }
            }

            // RT-3: Sala activada → notificar a ambos participantes
            case "events.room_activated" -> {
                JsonNode root = objectMapper.readTree(body);
                String reservationId = root.get("reservationId").asText();
                JsonNode participants = root.get("participantIds");
                String roomEvent = objectMapper.writeValueAsString(
                        Map.of("type", "SALA_ACTUALIZADA", "reservationId", reservationId));
                for (JsonNode node : participants) {
                    messagingTemplate.convertAndSendToUser(node.asText(), "/queue/salas", roomEvent);
                }
            }

            default -> log.debug("[EDA] Canal no manejado: {}", channel);
        }
    }
}
