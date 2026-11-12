package co.zeal.realtime.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/** R2-30 · Conexiones WebSocket/STOMP abiertas y personas conectadas en ESTA réplica (Prometheus suma réplicas). */
@Component
public class WebSocketMetrics {

    public WebSocketMetrics(MeterRegistry meters, SimpUserRegistry localUsers) {
        Gauge.builder("zeal.realtime.websocket.sessions", localUsers,
                        users -> users.getUsers().stream().mapToInt(user -> user.getSessions().size()).sum())
                .description("Sesiones STOMP abiertas en esta réplica")
                .register(meters);
        Gauge.builder("zeal.realtime.websocket.users", localUsers, SimpUserRegistry::getUserCount)
                .description("Personas con al menos una sesión en esta réplica")
                .register(meters);
    }
}
