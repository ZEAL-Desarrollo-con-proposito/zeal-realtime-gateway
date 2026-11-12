package co.zeal.realtime.broker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Conexión con RabbitMQ (plugin STOMP), el broker externo que comparten todas las réplicas del
 * gateway (HU-DT-01, ADR-04). {@code client*} son las credenciales con las que el relay abre una
 * conexión TCP por cada sesión de navegador; {@code system*} las de su conexión compartida, por la
 * que viajan los mensajes que publica el propio servidor.
 */
@Validated
@ConfigurationProperties("app.broker.relay")
public record BrokerRelayProperties(@NotBlank String host, @Positive int port, @NotBlank String virtualHost,
                                    @NotBlank String clientLogin, @NotBlank String clientPasscode,
                                    @NotBlank String systemLogin, @NotBlank String systemPasscode) {

    @Override
    public String toString() {
        return "BrokerRelayProperties[" + host + ":" + port + virtualHost + "]"; // Nunca imprime contraseñas.
    }
}
