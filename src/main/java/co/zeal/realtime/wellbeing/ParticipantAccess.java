package co.zeal.realtime.wellbeing;

import co.zeal.realtime.websocket.StompPrincipal;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Optional;

/** Autoriza a una persona sobre salas y conversaciones, con caché de los accesos concedidos. */
@Component
public class ParticipantAccess {

    private final WellbeingClient wellbeing;
    private final AccessCache<WellbeingClient.RoomAccess> rooms;
    private final AccessCache<WellbeingClient.ConversationAccess> conversations;

    public ParticipantAccess(WellbeingClient wellbeing, WellbeingProperties properties, StringRedisTemplate redis,
                             ObjectMapper objectMapper) {
        this.wellbeing = wellbeing;
        this.rooms = new AccessCache<>(redis, objectMapper, "rooms", WellbeingClient.RoomAccess.class,
                properties.accessCacheTtl());
        this.conversations = new AccessCache<>(redis, objectMapper, "conversations",
                WellbeingClient.ConversationAccess.class, properties.accessCacheTtl());
    }

    public Optional<WellbeingClient.RoomAccess> room(StompPrincipal principal, String reservationId) {
        return rooms.get(reservationId, principal.getName(),
                () -> wellbeing.room(principal.accessToken(), reservationId));
    }

    public Optional<WellbeingClient.ConversationAccess> conversation(StompPrincipal principal, String conversationId) {
        return conversations.get(conversationId, principal.getName(),
                () -> wellbeing.conversation(principal.accessToken(), conversationId));
    }

    /** La sala cambió de estado (p. ej. se activó): la próxima señal vuelve a consultar. */
    public void forgetRoom(String reservationId) {
        rooms.invalidate(reservationId);
    }
}
