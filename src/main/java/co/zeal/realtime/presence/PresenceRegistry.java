package co.zeal.realtime.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones STOMP abiertas por persona. Alguien está en línea mientras tenga al menos una pestaña
 * conectada. Vive en memoria, coherente con el broker simple de una sola instancia del MVP.
 */
@Component
public class PresenceRegistry {

    private final Map<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    /** @return {@code true} si es la primera sesión de la persona (pasa a estar en línea). */
    public boolean connect(String userId, String sessionId) {
        boolean[] firstSession = {false};
        sessionsByUser.compute(userId, (id, sessions) -> {
            Set<String> current = sessions == null ? ConcurrentHashMap.newKeySet() : sessions;
            firstSession[0] = current.isEmpty();
            current.add(sessionId);
            return current;
        });
        return firstSession[0];
    }

    /** @return {@code true} si era su última sesión (pasa a estar desconectada). */
    public boolean disconnect(String userId, String sessionId) {
        boolean[] lastSession = {false};
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            boolean removed = sessions.remove(sessionId);
            lastSession[0] = removed && sessions.isEmpty();
            return sessions.isEmpty() ? null : sessions;
        });
        return lastSession[0];
    }

    public PresenceStatus statusOf(String userId) {
        return sessionsByUser.containsKey(userId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE;
    }
}
