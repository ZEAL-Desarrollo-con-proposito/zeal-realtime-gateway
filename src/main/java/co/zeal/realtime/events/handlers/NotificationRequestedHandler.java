package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** HU-06: cualquier servicio pide una notificación; aquí se guarda y se entrega en vivo. */
@Component
class NotificationRequestedHandler implements IntegrationEventHandler {

    private final NotificationService notifications;

    NotificationRequestedHandler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public String channel() {
        return Channels.NOTIFICATIONS;
    }

    @Override
    public void handle(JsonNode event) {
        JsonNode link = event.get("link");
        notifications.notify(JsonNodes.text(event, "userId"), JsonNodes.text(event, "type"),
                JsonNodes.text(event, "title"), JsonNodes.text(event, "message"),
                link == null || link.isNull() ? null : link.asText());
    }
}
