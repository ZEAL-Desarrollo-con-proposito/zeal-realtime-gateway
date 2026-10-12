package co.zeal.realtime.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistencia de las notificaciones de cada persona. */
public interface NotificationStore {

    void save(String userId, Notification notification);

    List<Notification> findAll(String userId);

    Optional<Notification> find(String userId, String notificationId);

    void saveAll(String userId, Collection<Notification> notifications);
}
