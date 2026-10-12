package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * HU-14 y RF-16: la convocatoria nueva aparece en el catálogo de los empleados de la empresa y en
 * el panel de sus administradores conectados.
 */
@Component
class ProjectPublishedHandler implements IntegrationEventHandler {

    private final RealtimeSender sender;

    ProjectPublishedHandler(RealtimeSender sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return Channels.PROJECT_PUBLISHED;
    }

    @Override
    public void handle(JsonNode event) {
        String organization = JsonNodes.text(event, "organization");
        sender.toTopic(Destinations.organizationProjectsTopic(organization),
                Map.of("type", "PUBLICADO", "project", JsonNodes.object(event, "detail")));
        sender.toConnectedAdmins(organization, Destinations.ADMIN_PROJECTS_QUEUE,
                Map.of("type", "PUBLICADO", "project", JsonNodes.object(event, "project")));
    }
}
