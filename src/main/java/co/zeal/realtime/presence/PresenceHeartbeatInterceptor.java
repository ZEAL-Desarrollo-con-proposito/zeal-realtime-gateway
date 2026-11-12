package co.zeal.realtime.presence;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Renueva la presencia con el latido STOMP del navegador (cada 10 s) y con cualquier SEND. Los
 * latidos pasan por el canal de entrada de camino a RabbitMQ, así que este es el punto natural
 * para verlos sin abrir otro temporizador por conexión.
 */
@Component
public class PresenceHeartbeatInterceptor implements ChannelInterceptor {

    private final PresenceTracker tracker;

    /** {@code @Lazy}: el tracker depende del broker, que a su vez se configura con este interceptor. */
    public PresenceHeartbeatInterceptor(@Lazy PresenceTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
        if (type == SimpMessageType.HEARTBEAT || type == SimpMessageType.MESSAGE) {
            Principal user = SimpMessageHeaderAccessor.getUser(message.getHeaders());
            String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
            if (user != null && sessionId != null) {
                tracker.heartbeat(user.getName(), sessionId);
            }
        }
        return message;
    }
}
