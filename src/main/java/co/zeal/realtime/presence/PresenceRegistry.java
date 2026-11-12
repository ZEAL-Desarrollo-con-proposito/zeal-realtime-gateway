package co.zeal.realtime.presence;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Presencia compartida por todas las réplicas (HU-DT-01 / DT-2).
 *
 * <p>Por persona hay un sorted set {@code zeal:presence:user:{id}} con una entrada por conexión
 * (pestaña) y su hora de vencimiento como puntaje. Cada latido STOMP la renueva; una persona está
 * en línea mientras le quede al menos una conexión vigente, en cualquier réplica. El conjunto
 * {@code zeal:presence:users} indexa a quienes tienen conexiones para el barrido.
 *
 * <p>Cada transición (primera conexión vigente / última que se va) se decide dentro de un script
 * Lua, atómico en Redis: aunque dos réplicas actúen a la vez sobre la misma persona, solo una ve
 * el cambio y lo anuncia una sola vez.
 */
@Component
public class PresenceRegistry {

    static final String USERS_KEY = "zeal:presence:users";
    static final String USER_KEY_PREFIX = "zeal:presence:user:";

    /** Alta o renovación de una conexión. Devuelve 1 si la persona pasó a estar en línea. */
    private static final RedisScript<Long> TOUCH = RedisScript.of("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local before = redis.call('ZCARD', KEYS[1])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[5])
            if before == 0 then return 1 else return 0 end
            """, Long.class);

    /** Cierre de una conexión. Devuelve 1 si era la última vigente (pasa a desconectada). */
    private static final RedisScript<Long> REMOVE = RedisScript.of("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local remaining = redis.call('ZCARD', KEYS[1])
            if remaining == 0 then
              redis.call('DEL', KEYS[1])
              redis.call('SREM', KEYS[2], ARGV[3])
            end
            if removed == 1 and remaining == 0 then return 1 else return 0 end
            """, Long.class);

    /** Retira las conexiones vencidas. Devuelve 1 si con eso la persona quedó desconectada. */
    private static final RedisScript<Long> EXPIRE = RedisScript.of("""
            local removed = redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local remaining = redis.call('ZCARD', KEYS[1])
            if remaining == 0 then
              redis.call('DEL', KEYS[1])
              redis.call('SREM', KEYS[2], ARGV[2])
            end
            if removed > 0 and remaining == 0 then return 1 else return 0 end
            """, Long.class);

    private final StringRedisTemplate redis;
    private final PresenceProperties properties;
    private final Clock clock;

    public PresenceRegistry(StringRedisTemplate redis, PresenceProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return {@code true} si con esta conexión la persona pasa a estar en línea. */
    public boolean touch(String userId, String connectionId) {
        long now = clock.millis();
        long ttl = properties.ttl().toMillis();
        Long changed = redis.execute(TOUCH, List.of(userKey(userId), USERS_KEY), String.valueOf(now),
                String.valueOf(now + ttl), connectionId, String.valueOf(ttl * 2), userId);
        return Long.valueOf(1).equals(changed);
    }

    /** @return {@code true} si era su última conexión vigente (pasa a estar desconectada). */
    public boolean disconnect(String userId, String connectionId) {
        Long changed = redis.execute(REMOVE, List.of(userKey(userId), USERS_KEY), String.valueOf(clock.millis()),
                connectionId, userId);
        return Long.valueOf(1).equals(changed);
    }

    /** @return {@code true} si al retirar sus conexiones vencidas la persona quedó desconectada. */
    public boolean expire(String userId) {
        Long changed = redis.execute(EXPIRE, List.of(userKey(userId), USERS_KEY), String.valueOf(clock.millis()),
                userId);
        return Long.valueOf(1).equals(changed);
    }

    public Set<String> usersWithConnections() {
        Set<String> members = redis.opsForSet().members(USERS_KEY);
        return members == null ? Set.of() : members;
    }

    public PresenceStatus statusOf(String userId) {
        Long live = redis.opsForZSet().count(userKey(userId), clock.millis(), Double.POSITIVE_INFINITY);
        return live != null && live > 0 ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE;
    }

    private static String userKey(String userId) {
        return USER_KEY_PREFIX + userId;
    }
}
