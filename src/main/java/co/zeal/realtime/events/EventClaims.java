package co.zeal.realtime.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Garantiza que cada evento de dominio lo procese una sola réplica del gateway (HU-DT-01).
 *
 * <p>Redis Pub/Sub entrega cada evento a todas las réplicas suscritas. Si todas lo procesaran, los
 * suscriptores de un tópico recibirían una copia por réplica y las notificaciones se guardarían
 * varias veces. La primera réplica que hace {@code SET NX} sobre la huella del evento lo procesa;
 * las demás lo descartan. La huella es el {@code eventId} si el productor lo envía (R2-24) o, para
 * los productores anteriores, el SHA-256 del canal y los bytes del mensaje, idénticos en todas las
 * réplicas.
 */
@Component
public class EventClaims {

    private static final String KEY_PREFIX = "zeal:rt:event-claims:";

    private final StringRedisTemplate redis;
    private final Duration window;

    public EventClaims(StringRedisTemplate redis, @Value("${app.events.claim-window:30s}") Duration window) {
        this.redis = redis;
        this.window = window;
    }

    /** @return {@code true} si esta réplica debe procesar el evento. */
    public boolean claim(String channel, String eventId, byte[] body) {
        String fingerprint = eventId != null && !eventId.isBlank() ? "id:" + eventId : "sha:" + sha256(channel, body);
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY_PREFIX + channel + ":" + fingerprint,
                "1", window));
    }

    private static String sha256(String channel, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(channel.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
