package co.zeal.realtime.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Un hash de Redis por persona: {@code id → notificación en JSON}. Sobrevive a reinicios del gateway. */
@Component
public class RedisNotificationStore implements NotificationStore {

    private static final String KEY_PREFIX = "zeal:notifications:";

    private final HashOperations<String, String, String> hashes;
    private final ObjectMapper objectMapper;
    private final NotificationProperties properties;

    public RedisNotificationStore(StringRedisTemplate redis, ObjectMapper objectMapper,
                                  NotificationProperties properties) {
        this.hashes = redis.opsForHash();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void save(String userId, Notification notification) {
        hashes.put(key(userId), notification.id(), write(notification));
        discardOldest(userId);
    }

    @Override
    public void saveAll(String userId, Collection<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        hashes.putAll(key(userId), notifications.stream()
                .collect(Collectors.toMap(Notification::id, this::write)));
    }

    @Override
    public List<Notification> findAll(String userId) {
        return hashes.values(key(userId)).stream()
                .map(this::read)
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Notification> find(String userId, String notificationId) {
        return Optional.ofNullable(hashes.get(key(userId), notificationId)).map(this::read);
    }

    private void discardOldest(String userId) {
        List<Notification> all = findAll(userId);
        if (all.size() > properties.maxPerUser()) {
            Object[] stale = all.subList(properties.maxPerUser(), all.size()).stream()
                    .map(Notification::id)
                    .toArray();
            hashes.delete(key(userId), stale);
        }
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }

    private String write(Notification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la notificación", e);
        }
    }

    private Notification read(String json) {
        try {
            return objectMapper.readValue(json, Notification.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Notificación corrupta en Redis", e);
        }
    }

}
