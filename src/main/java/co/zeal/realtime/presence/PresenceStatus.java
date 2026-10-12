package co.zeal.realtime.presence;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PresenceStatus {
    ONLINE,
    OFFLINE;

    /** El frontend usa {@code 'online' | 'offline'}. */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
