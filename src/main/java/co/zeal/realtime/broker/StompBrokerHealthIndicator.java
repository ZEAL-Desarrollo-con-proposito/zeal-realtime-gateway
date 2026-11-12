package co.zeal.realtime.broker;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

/**
 * R2-3: la réplica solo está lista si su relay tiene la conexión de sistema con RabbitMQ. Sin ella
 * no puede publicar ni entregar nada; Docker/NGINX la sacan de la rotación hasta que reconecte.
 * Se nombra {@code stompBroker} en el grupo de readiness.
 */
@Component("stompBroker")
public class StompBrokerHealthIndicator implements HealthIndicator {

    private volatile boolean available;

    @EventListener
    public void onAvailability(BrokerAvailabilityEvent event) {
        available = event.isBrokerAvailable();
    }

    @Override
    public Health health() {
        return available ? Health.up().build() : Health.down().withDetail("broker", "RabbitMQ STOMP no disponible").build();
    }
}
