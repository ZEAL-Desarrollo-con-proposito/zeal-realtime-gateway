package co.zeal.realtime.notification;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cuántas notificaciones se conservan por persona (las más antiguas se descartan). */
@Validated
@ConfigurationProperties("app.notifications")
public record NotificationProperties(@Positive int maxPerUser) {
}
