package co.zeal.realtime.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Secreto y emisor del JWT que emite el Wellbeing Service (HS256). Este servicio solo lo valida. */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "El secreto JWT debe tener al menos 256 bits") String secret,
        @NotBlank String issuer) {
}
