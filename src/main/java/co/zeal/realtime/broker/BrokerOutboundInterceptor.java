package co.zeal.realtime.broker;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Devuelve al navegador los MESSAGE de tópicos con el nombre que él usó al suscribirse
 * ({@code /topic/a.b} → {@code /topic/a/b}). El cliente enruta por id de suscripción, pero así la
 * cabecera {@code destination} sigue siendo exactamente la del contrato.
 */
public class BrokerOutboundInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor original = StompHeaderAccessor.wrap(message);
        String destination = original.getDestination();
        if (original.getCommand() != StompCommand.MESSAGE || destination == null) {
            return message;
        }
        String translated = BrokerDestinations.fromBroker(destination);
        if (translated.equals(destination)) {
            return message;
        }
        original.setDestination(translated);
        return MessageBuilder.createMessage(message.getPayload(), original.getMessageHeaders());
    }
}
