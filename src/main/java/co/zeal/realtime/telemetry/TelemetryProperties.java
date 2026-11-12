package co.zeal.realtime.telemetry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * R2-32 · Límites de la telemetría del frontend. {@code allowedNames} acota la cardinalidad de las
 * métricas: un nombre fuera de la lista se registra como {@code otro}.
 */
@Validated
@ConfigurationProperties("app.telemetry")
public record TelemetryProperties(@Positive int maxBodyBytes, @Positive int requestsPerMinute,
                                  @NotNull Set<String> allowedNames) {
}
