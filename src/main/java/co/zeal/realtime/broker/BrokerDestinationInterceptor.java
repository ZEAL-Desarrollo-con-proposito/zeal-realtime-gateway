package co.zeal.realtime.broker;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Adapta los frames que van hacia RabbitMQ (canales de entrada del cliente y del broker):
 * <ul>
 *   <li>{@code /topic/a/b} pasa a {@code /topic/a.b} ({@link BrokerDestinations}).</li>
 *   <li>Las colas privadas ({@code /queue/notificaciones-user{sesión}}, que Spring deriva de
 *   {@code /user/queue/...}) se declaran en el SUBSCRIBE con {@code durable:false} y
 *   {@code auto-delete:true}: RabbitMQ las crea exclusivas de la conexión de esa sesión y las borra
 *   al cerrarse. Sin esto quedarían durables y huérfanas.</li>
 *   <li>Lo que el servidor publica en esas colas viaja como {@code /amq/queue/...}, que entrega en
 *   una cola existente sin declararla. Un SEND a {@code /queue/...} intentaría redeclarar la cola
 *   exclusiva de otra conexión y RabbitMQ respondería RESOURCE_LOCKED cerrando la conexión
 *   compartida del relay. Si la sesión ya se cerró, el mensaje simplemente se descarta.</li>
 * </ul>
 * En el canal de entrada del cliente debe ir después del guardia de suscripciones, que autoriza
 * con los nombres originales.
 */
public class BrokerDestinationInterceptor implements ChannelInterceptor {

    static final String DURABLE = "durable";
    static final String AUTO_DELETE = "auto-delete";
    private static final String QUEUE = "/queue/";
    public static final String EXISTING_QUEUE = "/amq/queue/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Se conserva el tipo de accessor del mensaje: el relay reutiliza (y modifica) un
        // StompHeaderAccessor tal cual, pero envuelve en uno nuevo los SimpMessageHeaderAccessor
        // de lo que publica el servidor.
        SimpMessageHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor.class) instanceof StompHeaderAccessor
                        ? StompHeaderAccessor.wrap(message)
                        : SimpMessageHeaderAccessor.wrap(message);
        String destination = accessor.getDestination();
        if (destination == null || !appliesTo(accessor.getMessageType())) {
            return message;
        }
        boolean privateQueue = destination.startsWith(QUEUE);
        if (privateQueue && accessor.getMessageType() == SimpMessageType.SUBSCRIBE) {
            accessor.setNativeHeader(DURABLE, "false");
            accessor.setNativeHeader(AUTO_DELETE, "true");
        } else if (privateQueue) {
            accessor.setDestination(EXISTING_QUEUE + destination.substring(QUEUE.length()));
        } else {
            String translated = BrokerDestinations.toBroker(destination);
            if (translated.equals(destination)) {
                return message;
            }
            accessor.setDestination(translated);
        }
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private static boolean appliesTo(SimpMessageType type) {
        return type == SimpMessageType.SUBSCRIBE || type == SimpMessageType.MESSAGE;
    }
}
