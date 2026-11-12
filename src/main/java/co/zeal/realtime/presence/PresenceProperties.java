package co.zeal.realtime.presence;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param ttl           vigencia de cada conexión sin latidos; el latido STOMP (10 s) la renueva.
 * @param sweepInterval cada cuánto una réplica (con bloqueo distribuido) retira las conexiones vencidas.
 */
@Validated
@ConfigurationProperties("app.presence")
public record PresenceProperties(@NotNull Duration ttl, @NotNull Duration sweepInterval) {
}
