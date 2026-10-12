package co.zeal.realtime.wellbeing;

import co.zeal.realtime.websocket.StompPrincipal;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/** Autoriza a una persona sobre salas y conversaciones, con caché de los accesos concedidos. */
@Component
public class ParticipantAccess {

    private final WellbeingClient wellbeing;
    private final AccessCache<WellbeingClient.RoomAccess> rooms;
    private final AccessCache<WellbeingClient.ConversationAccess> conversations;

    public ParticipantAccess(WellbeingClient wellbeing, WellbeingProperties properties, Clock clock) {
        this.wellbeing = wellbeing;
        this.rooms = new AccessCache<>(properties.accessCacheTtl(), clock);
        this.conversations = new AccessCache<>(properties.accessCacheTtl(), clock);
    }

    public Optional<WellbeingClient.RoomAccess> room(StompPrincipal principal, String reservationId) {
        return rooms.get(principal.getName() + "|" + reservationId,
                () -> wellbeing.room(principal.accessToken(), reservationId));
    }

    public Optional<WellbeingClient.ConversationAccess> conversation(StompPrincipal principal, String conversationId) {
        return conversations.get(principal.getName() + "|" + conversationId,
                () -> wellbeing.conversation(principal.accessToken(), conversationId));
    }

    /** La sala cambió de estado (p. ej. se activó): la próxima señal vuelve a consultar. */
    public void forgetRoom(String reservationId) {
        rooms.invalidateEndingWith("|" + reservationId);
    }
}
