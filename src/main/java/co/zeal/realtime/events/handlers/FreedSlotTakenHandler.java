package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Reto C-3: quienes perdieron la carrera retiran la oferta al instante. */
@Component
class FreedSlotTakenHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;

    FreedSlotTakenHandler(RealtimeSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return Channels.FREED_SLOT_TAKEN;
    }

    @Override
    public void handle(JsonNode event) {
        String winnerId = JsonNodes.text(event, "winnerId");
        var losers = JsonNodes.texts(event, "recipientIds").stream().filter(id -> !id.equals(winnerId)).toList();
        sender.toUsers(losers, Destinations.FREED_SLOTS_QUEUE,
                Map.of("type", "TOMADO", "offerId", JsonNodes.text(event, "offerId")));
    }
}
