package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** HU-15: el taller nuevo se anuncia a la empresa y a sus administradores conectados. */
@Component
class WorkshopPublishedHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;

    WorkshopPublishedHandler(RealtimeSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return Channels.WORKSHOP_PUBLISHED;
    }

    @Override
    public void handle(JsonNode event) {
        String organization = JsonNodes.text(event, "organization");
        Map<String, Object> published = Map.of("type", "PUBLICADO", "workshop", JsonNodes.object(event, "workshop"));
        sender.toTopic(Destinations.organizationWorkshopsTopic(organization), published);
        sender.toConnectedAdmins(organization, Destinations.ADMIN_WORKSHOPS_QUEUE, published);
    }
}
