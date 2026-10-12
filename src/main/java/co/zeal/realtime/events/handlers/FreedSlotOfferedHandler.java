package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Reto C-3 + RT-2: la oferta llega solo a la cola privada de cada candidato. */
@Component
class FreedSlotOfferedHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;

    FreedSlotOfferedHandler(RealtimeSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return Channels.FREED_SLOT_OFFERED;
    }

    @Override
    public void handle(JsonNode event) {
        sender.toUsers(JsonNodes.texts(event, "recipientIds"), Destinations.FREED_SLOTS_QUEUE,
                Map.of("type", "LIBERADO", "offer", JsonNodes.object(event, "offer")));
    }
}
