package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.Role;
import co.zeal.realtime.security.GatewaySecurityAudit;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Autoriza cada SUBSCRIBE y SEND. Las colas privadas ({@code /user/queue/...}) ya están aisladas
 * por persona; los tópicos se restringen por rol y por empresa para que nadie escuche datos de
 * otra organización ni el Business Domain vea el Wellbeing Domain.
 */
@Component
public class StompSubscriptionGuard implements ChannelInterceptor {

    private final GatewaySecurityAudit audit;

    public StompSubscriptionGuard(GatewaySecurityAudit audit) {
        this.audit = audit;
    }

    private static final Pattern USER_QUEUE = Pattern.compile("^/user/queue/[a-z-]+$");
    private static final Pattern SPOTS_TOPIC = Pattern.compile("^/topic/(proyectos|talleres)/[^/]+/cupos$");
    private static final Pattern ORGANIZATION_TOPIC =
            Pattern.compile("^/topic/organizaciones/([^/]+)/(proyectos|talleres)$");
    private static final Pattern PRESENCE_TOPIC = Pattern.compile("^/topic/presencia/[^/]+$");
    private static final Pattern APP_DESTINATION = Pattern.compile("^/app/(salas|chats)/[^/]+/[a-z]+$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SUBSCRIBE) {
            StompPrincipal principal = principalOf(accessor);
            requireAllowed(isSubscriptionAllowed(principal, accessor.getDestination()), principal, accessor,
                    "STOMP_SUSCRIPCION");
        } else if (command == StompCommand.SEND) {
            StompPrincipal principal = principalOf(accessor);
            String destination = accessor.getDestination();
            requireAllowed(destination != null && APP_DESTINATION.matcher(destination).matches(), principal, accessor,
                    "STOMP_ENVIO");
        }
        return message;
    }

    static boolean isSubscriptionAllowed(StompPrincipal principal, String destination) {
        if (destination == null) {
            return false;
        }
        Role role = principal.user().role();
        boolean wellbeingMember = role == Role.EMPLEADO || role == Role.PSICOLOGO;
        if (USER_QUEUE.matcher(destination).matches() || SPOTS_TOPIC.matcher(destination).matches()) {
            return true;
        }
        if (Destinations.SLOTS_TOPIC.equals(destination) || PRESENCE_TOPIC.matcher(destination).matches()) {
            return wellbeingMember;
        }
        var organizationTopic = ORGANIZATION_TOPIC.matcher(destination);
        if (organizationTopic.matches()) {
            return organizationTopic.group(1).equals(Destinations.encode(principal.user().organization()));
        }
        return false;
    }

    private static StompPrincipal principalOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("Sesión en tiempo real sin autenticar");
    }

    /** R2-9: el intento de escuchar o escribir en un canal ajeno se rechaza (cierra la sesión) y se audita. */
    private void requireAllowed(boolean allowed, StompPrincipal principal, StompHeaderAccessor accessor, String event) {
        if (!allowed) {
            audit.publish(event, "DENEGADO", principal.getName(), accessor, "destino " + accessor.getDestination());
            throw new AccessDeniedException("No tienes permiso para este canal");
        }
    }
}
