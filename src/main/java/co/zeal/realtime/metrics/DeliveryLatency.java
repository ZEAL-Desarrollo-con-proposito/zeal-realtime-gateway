package co.zeal.realtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * R2-24 / REND-1 · Tiempo entre el commit que originó un cambio y su envío efectivo por STOMP, como
 * histograma (Prometheus calcula p50/p95/p99 por canal). Las etiquetas son el canal de Redis o "chat":
 * cardinalidad acotada, nunca ids de personas o recursos.
 */
@Component
public class DeliveryLatency {

    public static final String METRIC = "zeal.realtime.delivery.latency";

    private final MeterRegistry meters;
    private final Clock clock;

    public DeliveryLatency(MeterRegistry meters, Clock clock) {
        this.meters = meters;
        this.clock = clock;
    }

    public void record(String source, String committedAtIso) {
        if (committedAtIso == null || committedAtIso.isBlank()) {
            return; // Productor anterior a R2-24: sin hora de commit.
        }
        try {
            record(source, Instant.parse(committedAtIso));
        } catch (DateTimeParseException e) {
            // Campo inválido: no se mide.
        }
    }

    public void record(String source, Instant committedAt) {
        Duration latency = Duration.between(committedAt, Instant.now(clock));
        if (!latency.isNegative()) {
            Timer.builder(METRIC)
                    .description("Commit en la base → envío por STOMP")
                    .tag("source", source)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofSeconds(10))
                    .register(meters)
                    .record(latency);
        }
    }
}
