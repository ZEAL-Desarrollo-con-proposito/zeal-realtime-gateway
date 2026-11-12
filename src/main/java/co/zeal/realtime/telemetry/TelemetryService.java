package co.zeal.realtime.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;

/**
 * R2-32 · Convierte la telemetría del frontend en métricas Micrometer y logs estructurados.
 *
 * <ul>
 *   <li>Límite por persona compartido entre réplicas: contador en Redis por minuto ({@code INCR} + TTL).</li>
 *   <li>Cardinalidad acotada: las etiquetas son solo {@code type} y {@code name} (lista permitida; lo
 *   demás es {@code otro}). La ruta va al log, nunca a una etiqueta.</li>
 *   <li>Sin datos personales: el usuario queda en el log como id interno (MDC), sin texto libre.</li>
 * </ul>
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger("zeal.frontend.telemetry");

    private final MeterRegistry meters;
    private final StringRedisTemplate redis;
    private final TelemetryProperties properties;
    private final Clock clock;

    public TelemetryService(MeterRegistry meters, StringRedisTemplate redis, TelemetryProperties properties,
                            Clock clock) {
        this.meters = meters;
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public void record(String userId, TelemetryBatch batch) {
        enforceRateLimit(userId);
        for (TelemetryBatch.Event event : batch.events()) {
            String name = properties.allowedNames().contains(event.name()) ? event.name() : "otro";
            switch (event.type()) {
                case error -> meters.counter("zeal.frontend.errors", "name", name).increment();
                case uso -> meters.counter("zeal.frontend.usage", "name", name).increment();
                case latencia -> {
                    if (event.valueMs() != null) {
                        Timer.builder("zeal.frontend.latency").tag("name", name).publishPercentileHistogram()
                                .maximumExpectedValue(Duration.ofMinutes(2)).register(meters)
                                .record(Duration.ofMillis(event.valueMs()));
                    }
                }
            }
            log.info("telemetria type={} name={} route={} valueMs={}", event.type(), name, event.route(), event.valueMs());
        }
    }

    private void enforceRateLimit(String userId) {
        String key = "zeal:telemetry:rate:" + userId + ":" + clock.millis() / 60_000;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        if (count != null && count > properties.requestsPerMinute()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados envíos de telemetría");
        }
    }
}
