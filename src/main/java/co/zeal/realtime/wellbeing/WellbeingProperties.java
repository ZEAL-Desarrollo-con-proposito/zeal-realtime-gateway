package co.zeal.realtime.wellbeing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Conexión con el Wellbeing Service, dueño de las salas y conversaciones. */
@Validated
@ConfigurationProperties("app.wellbeing")
public record WellbeingProperties(@NotBlank String baseUrl, @NotNull Duration timeout,
                                  @NotNull Duration accessCacheTtl) {
}
