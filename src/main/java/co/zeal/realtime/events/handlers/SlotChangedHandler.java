package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** RT-2: los directorios y agendas abiertos se actualizan sin recargar ({@code {type, slot}}). */
@Component
class SlotChangedHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;

    SlotChangedHandler(RealtimeSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return Channels.SLOTS;
    }

    @Override
    public void handle(JsonNode event) {
        sender.toTopic(Destinations.SLOTS_TOPIC, event);
    }
}
