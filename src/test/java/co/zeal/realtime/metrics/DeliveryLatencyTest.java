package co.zeal.realtime.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryLatencyTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00.250Z");

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final DeliveryLatency latency = new DeliveryLatency(meters, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void measuresFromCommitToDeliveryPerSource() {
        latency.record("zeal.marketplace.projects.spots", "2026-11-11T15:00:00.050Z");
        latency.record("chat", Instant.parse("2026-11-11T15:00:00.150Z"));

        Timer spots = meters.find(DeliveryLatency.METRIC).tag("source", "zeal.marketplace.projects.spots").timer();
        assertThat(spots.count()).isEqualTo(1);
        assertThat(spots.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(200.0);
        assertThat(meters.find(DeliveryLatency.METRIC).tag("source", "chat").timer().totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(100.0);
    }

    @Test
    void ignoresEventsWithoutAValidCommitTime() {
        latency.record("zeal.wellbeing.slots", (String) null);
        latency.record("zeal.wellbeing.slots", "null");
        latency.record("zeal.wellbeing.slots", "2026-11-11T15:00:01Z"); // en el futuro: relojes desfasados

        assertThat(meters.find(DeliveryLatency.METRIC).timers()).allMatch(timer -> timer.count() == 0);
    }
}
