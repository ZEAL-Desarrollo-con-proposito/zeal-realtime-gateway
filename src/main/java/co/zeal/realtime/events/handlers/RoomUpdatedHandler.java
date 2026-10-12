package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.wellbeing.ParticipantAccess;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** RT-3: la sala se activó; ambos participantes recargan su estado y la señalización se habilita. */
@Component
class RoomUpdatedHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;
    private final ParticipantAccess access;

    RoomUpdatedHandler(RealtimeSender sender, ParticipantAccess access) {
        this.sender = sender;
        this.access = access;
    }

    @Override
    public String channel() {
        return Channels.ROOM_UPDATED;
    }

    @Override
    public void handle(JsonNode event) {
        String reservationId = JsonNodes.text(event, "reservationId");
        access.forgetRoom(reservationId);
        sender.toUsers(JsonNodes.texts(event, "participantIds"), Destinations.ROOMS_QUEUE,
                Map.of("type", "SALA_ACTUALIZADA", "reservationId", reservationId));
    }
}
