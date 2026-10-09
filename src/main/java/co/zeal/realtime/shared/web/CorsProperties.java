package co.zeal.realtime.shared.web;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Orígenes del frontend autorizados a llamar la API desde el navegador. */
@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
