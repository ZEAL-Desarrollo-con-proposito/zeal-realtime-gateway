package co.zeal.realtime.websocket;

import co.zeal.realtime.security.ClientIpHandshakeInterceptor;
import co.zeal.realtime.shared.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * R2-29 · correlationId y userId en el MDC mientras se procesa cada frame STOMP (chat, señales). Se toma
 * de la cabecera {@code x-request-id} del frame si el cliente la envía; si no, del handshake (NGINX); si
 * tampoco, uno nuevo por mensaje.
 */
public class StompMdcInterceptor implements ExecutorChannelInterceptor {

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        String correlationId = accessor == null ? null : accessor.getFirstNativeHeader("x-request-id");
        if (correlationId == null) {
            Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
            correlationId = attributes == null ? null : (String) attributes.get(ClientIpHandshakeInterceptor.CORRELATION_ID);
        }
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId != null && correlationId.length() <= 64
                ? correlationId : UUID.randomUUID().toString());
        Principal user = SimpMessageHeaderAccessor.getUser(message.getHeaders());
        if (user != null) {
            MDC.put(CorrelationIdFilter.USER_KEY, user.getName());
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        MDC.remove(CorrelationIdFilter.USER_KEY);
    }
}
