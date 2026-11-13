package co.zeal.realtime.websocket;

import co.zeal.realtime.delivery.UserDelivery;
import co.zeal.realtime.delivery.UserDeliveryFanout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Fachada de envío. Los tópicos van directo al broker compartido (RabbitMQ), que los reparte a
 * los suscriptores de todas las réplicas. Las colas privadas pasan por {@link UserDeliveryFanout}
 * para que cada réplica las entregue a sus propias sesiones.
 */
@Component
public class RealtimeSender {

    public static final String EMITTED_AT = "emittedAt";

    private final SimpMessagingTemplate template;
    private final UserDeliveryFanout fanout;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RealtimeSender(SimpMessagingTemplate template, UserDeliveryFanout fanout, ObjectMapper objectMapper,
                          Clock clock) {
        this.template = template;
        this.fanout = fanout;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * R2-24 / REND-1: todo aviso que sale por STOMP lleva {@code emittedAt} (ISO-8601 UTC, el instante en
     * que el gateway lo publica). El frontend mide con él la latencia hasta pintarlo en pantalla. Es un
     * campo nuevo en la raíz del mensaje: los consumidores que no lo conocen lo ignoran.
     */
    private JsonNode stamped(Object payload) {
        JsonNode tree = objectMapper.valueToTree(payload);
        if (tree instanceof ObjectNode object) {
            ObjectNode copy = object.deepCopy(); // nunca se modifica el evento original
            copy.put(EMITTED_AT, Instant.now(clock).toString());
            return copy;
        }
        return tree;
    }

    public void toTopic(String topic, Object payload) {
        template.convertAndSend(topic, stamped(payload));
    }

    /** Llega a todas las sesiones abiertas de la persona ({@code /user/queue/...} en el cliente). */
    public void toUser(String userId, String queue, Object payload) {
        toUsers(List.of(userId), queue, payload);
    }

    public void toUsers(Collection<String> userIds, String queue, Object payload) {
        List<String> recipients = userIds.stream().distinct().toList();
        if (!recipients.isEmpty()) {
            fanout.publish(UserDelivery.toUsers(recipients, queue, stamped(payload)));
        }
    }

    /** A los administradores conectados de una empresa (y solo a ellos), en cualquier réplica. */
    public void toConnectedAdmins(String organization, String queue, Object payload) {
        fanout.publish(UserDelivery.toAdminsOf(organization, queue, stamped(payload)));
    }
}
