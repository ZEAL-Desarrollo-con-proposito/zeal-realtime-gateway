package co.zeal.realtime.websocket;

import co.zeal.realtime.delivery.UserDelivery;
import co.zeal.realtime.delivery.UserDeliveryFanout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Fachada de envío. Los tópicos van directo al broker compartido (RabbitMQ), que los reparte a
 * los suscriptores de todas las réplicas. Las colas privadas pasan por {@link UserDeliveryFanout}
 * para que cada réplica las entregue a sus propias sesiones.
 */
@Component
public class RealtimeSender {

    private final SimpMessagingTemplate template;
    private final UserDeliveryFanout fanout;
    private final ObjectMapper objectMapper;

    public RealtimeSender(SimpMessagingTemplate template, UserDeliveryFanout fanout, ObjectMapper objectMapper) {
        this.template = template;
        this.fanout = fanout;
        this.objectMapper = objectMapper;
    }

    public void toTopic(String topic, Object payload) {
        template.convertAndSend(topic, payload);
    }

    /** Llega a todas las sesiones abiertas de la persona ({@code /user/queue/...} en el cliente). */
    public void toUser(String userId, String queue, Object payload) {
        toUsers(List.of(userId), queue, payload);
    }

    public void toUsers(Collection<String> userIds, String queue, Object payload) {
        List<String> recipients = userIds.stream().distinct().toList();
        if (!recipients.isEmpty()) {
            fanout.publish(UserDelivery.toUsers(recipients, queue, objectMapper.valueToTree(payload)));
        }
    }

    /** A los administradores conectados de una empresa (y solo a ellos), en cualquier réplica. */
    public void toConnectedAdmins(String organization, String queue, Object payload) {
        fanout.publish(UserDelivery.toAdminsOf(organization, queue, objectMapper.valueToTree(payload)));
    }
}
