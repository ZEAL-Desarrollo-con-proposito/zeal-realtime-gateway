package co.zeal.realtime.security;

import co.zeal.realtime.websocket.StompPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.List;

/**
 * R2-13 / SEG-4 · Cuando el Wellbeing Service revoca una sesión o un token (reutilización de refresh
 * token, logout) lo difunde por Redis; cada réplica del gateway cierra en el acto las conexiones STOMP
 * que abrió con esos tokens. El navegador intentará reconectar y el CONNECT ya no pasará la lista negra.
 */
@Component
public class RevokedSessionCloser implements MessageListener {

    public static final String CHANNEL = "zeal.security.revocations";
    private static final Logger log = LoggerFactory.getLogger(RevokedSessionCloser.class);
    private static final CloseStatus REVOKED = CloseStatus.POLICY_VIOLATION.withReason("Sesión revocada");

    private final SimpUserRegistry localUsers;
    private final LiveSockets sockets;
    private final ObjectMapper objectMapper;

    public RevokedSessionCloser(SimpUserRegistry localUsers, LiveSockets sockets, ObjectMapper objectMapper) {
        this.localUsers = localUsers;
        this.sockets = sockets;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode revocation = objectMapper.readTree(message.getBody());
            String sessionId = revocation.path("sid").asText(null);
            String tokenId = revocation.path("jti").asText(null);
            List<String> closed = localUsers.getUsers().stream()
                    .filter(user -> user.getPrincipal() instanceof StompPrincipal principal
                            && principal.isCoveredBy(sessionId, tokenId))
                    .map(SimpUser::getSessions)
                    .flatMap(sessions -> sessions.stream().map(SimpSession::getId))
                    .filter(id -> sockets.close(id, REVOKED))
                    .toList();
            if (!closed.isEmpty()) {
                log.info("Conexiones cerradas por revocación ({}): {}", revocation.path("reason").asText(), closed.size());
            }
        } catch (Exception e) {
            log.error("No se pudo procesar una revocación", e);
        }
    }
}
