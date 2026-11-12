package co.zeal.realtime.delivery;

import co.zeal.realtime.shared.security.Role;
import co.zeal.realtime.websocket.StompPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/** Entrega en esta réplica lo publicado por {@link UserDeliveryFanout} desde cualquier réplica. */
@Component
public class LocalUserDelivery implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LocalUserDelivery.class);

    private final SimpMessagingTemplate template;
    private final SimpUserRegistry localUsers;
    private final ObjectMapper objectMapper;

    public LocalUserDelivery(SimpMessagingTemplate template, SimpUserRegistry localUsers, ObjectMapper objectMapper) {
        this.template = template;
        this.localUsers = localUsers;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliver(objectMapper.readValue(message.getBody(), UserDelivery.class));
        } catch (Exception e) {
            // Sin la excepción completa: el error de Jackson incluiría parte del contenido (R2-29).
            log.error("No se pudo entregar un mensaje privado: {}", e.getClass().getSimpleName());
        }
    }

    void deliver(UserDelivery delivery) {
        recipients(delivery)
                .filter(userId -> localUsers.getUser(userId) != null) // Sin sesiones aquí: nada que hacer.
                .forEach(userId -> template.convertAndSendToUser(userId, delivery.queue(), delivery.payload()));
    }

    private Stream<String> recipients(UserDelivery delivery) {
        if (delivery.adminsOf() == null) {
            return delivery.userIds().stream().distinct();
        }
        return localUsers.getUsers().stream()
                .map(SimpUser::getPrincipal)
                .filter(StompPrincipal.class::isInstance)
                .map(StompPrincipal.class::cast)
                .filter(principal -> principal.user().role() == Role.ADMIN)
                .filter(principal -> principal.user().organization().equals(delivery.adminsOf()))
                .map(StompPrincipal::getName)
                .distinct();
    }
}
