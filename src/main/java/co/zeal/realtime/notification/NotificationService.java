package co.zeal.realtime.notification;

import co.zeal.realtime.shared.error.ResourceNotFoundException;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de notificaciones (HU-06, RT-2): guarda cada aviso y lo empuja en vivo a todas las
 * sesiones de su destinatario. Marcar como leída se replica en sus otras pestañas.
 */
@Service
public class NotificationService {

    private final NotificationStore store;
    private final RealtimeSender sender;
    private final Clock clock;

    public NotificationService(NotificationStore store, RealtimeSender sender, Clock clock) {
        this.store = store;
        this.sender = sender;
        this.clock = clock;
    }

    public Notification notify(String userId, String type, String title, String message, String link) {
        Notification notification = new Notification("notificacion-" + UUID.randomUUID(), type, title, message,
                link, false, Instant.now(clock));
        store.save(userId, notification);
        sender.toUser(userId, Destinations.NOTIFICATIONS_QUEUE, Map.of("type", "NUEVA", "notification", notification));
        return notification;
    }

    public List<Notification> list(String userId) {
        return store.findAll(userId);
    }

    public void markRead(String userId, String notificationId) {
        Notification notification = store.find(userId, notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("La notificación ya no existe"));
        store.save(userId, notification.markRead());
        announceRead(userId, List.of(notificationId));
    }

    public void markAllRead(String userId) {
        List<Notification> unread = store.findAll(userId).stream().filter(n -> !n.read()).toList();
        store.saveAll(userId, unread.stream().map(Notification::markRead).toList());
        announceRead(userId, unread.stream().map(Notification::id).toList());
    }

    private void announceRead(String userId, List<String> ids) {
        sender.toUser(userId, Destinations.NOTIFICATIONS_QUEUE, Map.of("type", "LEIDAS", "ids", ids));
    }
}
