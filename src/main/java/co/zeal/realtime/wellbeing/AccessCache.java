package co.zeal.realtime.wellbeing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Caché corta de autorizaciones positivas, en Redis para que la compartan todas las réplicas
 * (HU-DT-01). Una videollamada envía decenas de candidatos ICE en segundos: sin caché, cada uno
 * costaría una llamada HTTP. Solo se guardan accesos concedidos.
 *
 * <p>Cada entrada pertenece a un grupo (la sala o la conversación) para poder olvidar de una vez
 * todos los accesos de ese grupo cuando cambia de estado.
 */
public final class AccessCache<T> {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String prefix;
    private final Class<T> type;
    private final Duration ttl;

    public AccessCache(StringRedisTemplate redis, ObjectMapper objectMapper, String name, Class<T> type,
                       Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.prefix = "zeal:rt:access:" + name + ":";
        this.type = type;
        this.ttl = ttl;
    }

    public Optional<T> get(String group, String member, Supplier<Optional<T>> loader) {
        String key = prefix + group + "|" + member;
        Optional<T> cached = Optional.ofNullable(redis.opsForValue().get(key)).map(this::read);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<T> loaded = loader.get();
        loaded.ifPresent(value -> {
            redis.opsForValue().set(key, write(value), ttl);
            String index = indexKey(group);
            redis.opsForSet().add(index, key);
            redis.expire(index, ttl);
        });
        return loaded;
    }

    /** Olvida todos los accesos del grupo (p. ej. de una sala que se activó). */
    public void invalidate(String group) {
        String index = indexKey(group);
        Set<String> keys = redis.opsForSet().members(index);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.delete(index);
    }

    private String indexKey(String group) {
        return prefix + "index:" + group;
    }

    private String write(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el acceso en caché", e);
        }
    }

    private T read(String json) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null; // Entrada ilegible: se trata como ausente y se vuelve a consultar.
        }
    }
}
