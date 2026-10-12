package co.zeal.realtime.wellbeing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto hacia el Wellbeing Service. Cada llamada viaja con el token de la persona: el gateway
 * no decide quién participa en una sala o conversación, se lo pregunta al dueño del dato.
 */
public interface WellbeingClient {

    /** La sala vista por esa persona, o vacío si no participa en ella. */
    Optional<RoomAccess> room(String accessToken, String reservationId);

    /** La conversación, o vacío si esa persona no puede usarla. */
    Optional<ConversationAccess> conversation(String accessToken, String conversationId);

    /** Guarda el mensaje en el historial y devuelve el mensaje completo; vacío si fue rechazado. */
    Optional<Map<String, Object>> sendChatMessage(String accessToken, String conversationId, String text);

    record RoomAccess(String reservationId, String status, Instant closesAt, String otherParticipantId) {

        public boolean acceptsSignalsAt(Instant now) {
            return "ACTIVA".equals(status) && !now.isAfter(closesAt);
        }
    }

    record ConversationAccess(String id, String employeeId, String psychologistId) {

        public String otherThan(String userId) {
            return employeeId.equals(userId) ? psychologistId : employeeId;
        }
    }
}
