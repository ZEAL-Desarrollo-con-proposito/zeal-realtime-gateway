package co.zeal.realtime.presence;

import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * HU-10: anuncia por {@code /topic/presencia/{id}} cuando alguien se conecta o se desconecta
 * (incluido el cierre abrupto de la pestaña, que el servidor detecta por el heartbeat).
 */
@Component
public class PresenceTracker {

    private final PresenceRegistry registry;
    private final RealtimeSender sender;

    public PresenceTracker(PresenceRegistry registry, RealtimeSender sender) {
        this.registry = registry;
        this.sender = sender;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (user != null && sessionId != null && registry.connect(user.getName(), sessionId)) {
            announce(user.getName(), PresenceStatus.ONLINE);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null && registry.disconnect(user.getName(), event.getSessionId())) {
            announce(user.getName(), PresenceStatus.OFFLINE);
        }
    }

    private void announce(String userId, PresenceStatus status) {
        sender.toTopic(Destinations.presenceTopic(userId), Map.of("type", "PRESENCIA", "status", status));
    }
}
