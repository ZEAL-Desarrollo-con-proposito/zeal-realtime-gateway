package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.Role;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Fachada sobre el broker STOMP: difusión por tópico, cola privada por persona y por rol. */
@Component
public class RealtimeSender {

    private final SimpMessagingTemplate template;
    private final SimpUserRegistry users;

    public RealtimeSender(SimpMessagingTemplate template, SimpUserRegistry users) {
        this.template = template;
        this.users = users;
    }

    public void toTopic(String topic, Object payload) {
        template.convertAndSend(topic, payload);
    }

    /** Llega a todas las sesiones abiertas de la persona ({@code /user/queue/...} en el cliente). */
    public void toUser(String userId, String queue, Object payload) {
        template.convertAndSendToUser(userId, queue, payload);
    }

    public void toUsers(Collection<String> userIds, String queue, Object payload) {
        userIds.stream().distinct().forEach(userId -> toUser(userId, queue, payload));
    }

    /** A los administradores conectados de una empresa (y solo a ellos). */
    public void toConnectedAdmins(String organization, String queue, Object payload) {
        users.getUsers().stream()
                .map(SimpUser::getPrincipal)
                .filter(StompPrincipal.class::isInstance)
                .map(StompPrincipal.class::cast)
                .filter(principal -> principal.user().role() == Role.ADMIN)
                .filter(principal -> principal.user().organization().equals(organization))
                .map(StompPrincipal::getName)
                .distinct()
                .forEach(adminId -> toUser(adminId, queue, payload));
    }
}
