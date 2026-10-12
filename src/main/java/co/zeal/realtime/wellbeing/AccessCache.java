package co.zeal.realtime.wellbeing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Caché corta de autorizaciones positivas. Una videollamada envía decenas de candidatos ICE en
 * segundos: sin caché, cada uno costaría una llamada HTTP. Solo se guardan accesos concedidos.
 */
public final class AccessCache<T> {

    private final Map<String, Entry<T>> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public AccessCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public Optional<T> get(String key, Supplier<Optional<T>> loader) {
        Instant now = Instant.now(clock);
        Entry<T> cached = entries.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(cached.value());
        }
        Optional<T> loaded = loader.get();
        loaded.ifPresentOrElse(value -> entries.put(key, new Entry<>(value, now.plus(ttl))),
                () -> entries.remove(key));
        return loaded;
    }

    /** Descarta las entradas cuya clave termina en {@code suffix} (p. ej. todas las de una sala). */
    public void invalidateEndingWith(String suffix) {
        entries.keySet().removeIf(key -> key.endsWith(suffix));
    }

    private record Entry<T>(T value, Instant expiresAt) {
    }
}
