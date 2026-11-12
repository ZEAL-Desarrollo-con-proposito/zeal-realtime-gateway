package co.zeal.realtime.presence;

import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * HU-10: anuncia por {@code /topic/presencia/{id}} cuando alguien se conecta o se desconecta.
 *
 * <p>Tres caminos llevan a "desconectado": el DISCONNECT o cierre del socket (inmediato); el
 * corte de latidos, que RabbitMQ detecta y que termina en el mismo cierre; y la caída de una
 * réplica entera, cuyas conexiones ya nadie renueva. Para este último caso, una sola réplica a la
 * vez ({@link SchedulerLock}) barre las conexiones vencidas.
 *
 * <p>Se eligió el barrido en lugar de las keyspace notifications de Redis porque éstas son
 * Pub/Sub sin entrega garantizada (si en ese instante no hay réplica escuchando, el aviso se
 * pierde), exigen activar {@code notify-keyspace-events} en el servidor y Redis emite el evento
 * {@code expired} cuando borra la clave, no cuando vence, lo que puede retrasarlo sin cota. El
 * barrido es determinista: cada conexión vencida se detecta en a lo sumo TTL + intervalo.
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
        if (user != null && sessionId != null) {
            heartbeat(user.getName(), sessionId);
        }
    }

    /** Cada latido (o frame) del cliente renueva su conexión. */
    public void heartbeat(String userId, String sessionId) {
        if (registry.touch(userId, sessionId)) {
            announce(userId, PresenceStatus.ONLINE);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null && registry.disconnect(user.getName(), event.getSessionId())) {
            announce(user.getName(), PresenceStatus.OFFLINE);
        }
    }

    @Scheduled(fixedDelayString = "${app.presence.sweep-interval}")
    @SchedulerLock(name = "presence-sweep", lockAtMostFor = "PT30S")
    public void sweepExpiredConnections() {
        registry.usersWithConnections().stream()
                .filter(registry::expire)
                .forEach(userId -> announce(userId, PresenceStatus.OFFLINE));
    }

    private void announce(String userId, PresenceStatus status) {
        sender.toTopic(Destinations.presenceTopic(userId), Map.of("type", "PRESENCIA", "status", status));
    }
}
