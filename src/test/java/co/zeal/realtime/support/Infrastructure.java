package co.zeal.realtime.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;
import java.util.Map;

/** Redis y RabbitMQ (con el plugin STOMP) reales, compartidos por todas las pruebas de integración. */
public final class Infrastructure {

    public static final String RABBIT_USER = "zeal";
    public static final String RABBIT_PASSWORD = "zeal-test-password";

    public static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    public static final GenericContainer<?> RABBITMQ = new GenericContainer<>("rabbitmq:4.2.9-management-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD)
            .withCopyToContainer(Transferable.of("[rabbitmq_management,rabbitmq_stomp]."), "/etc/rabbitmq/enabled_plugins")
            .withExposedPorts(61613, 15672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        REDIS.start();
        RABBITMQ.start();
    }

    private Infrastructure() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    /** Las mismas propiedades como argumentos, para levantar réplicas adicionales del gateway. */
    public static Map<String, Object> properties() {
        return Map.of(
                "spring.data.redis.host", REDIS.getHost(),
                "spring.data.redis.port", REDIS.getMappedPort(6379),
                "app.broker.relay.host", RABBITMQ.getHost(),
                "app.broker.relay.port", RABBITMQ.getMappedPort(61613),
                "app.broker.relay.client-login", RABBIT_USER,
                "app.broker.relay.client-passcode", RABBIT_PASSWORD,
                "app.broker.relay.system-login", RABBIT_USER,
                "app.broker.relay.system-passcode", RABBIT_PASSWORD);
    }
}
