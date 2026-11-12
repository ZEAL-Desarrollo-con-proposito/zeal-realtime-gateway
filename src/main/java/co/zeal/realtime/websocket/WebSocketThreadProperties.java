package co.zeal.realtime.websocket;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Hilos de los canales STOMP de entrada y salida (R2-27), ajustables por variables de entorno. */
@Validated
@ConfigurationProperties("app.websocket.threads")
public record WebSocketThreadProperties(@Positive int inbound, @Positive int outbound) {
}
