package co.zeal.realtime.notification;

import java.time.Instant;

/** Contrato de {@code isNotification()} del frontend. */
public record Notification(String id, String type, String title, String message, String link, boolean read,
                           Instant createdAt) {

    Notification markRead() {
        return read ? this : new Notification(id, type, title, message, link, true, createdAt);
    }
}
