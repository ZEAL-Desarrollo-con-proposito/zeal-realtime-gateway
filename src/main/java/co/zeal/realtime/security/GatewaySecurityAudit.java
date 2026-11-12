package co.zeal.realtime.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R2-9 / R2-14 · El gateway no tiene base de datos: sus eventos de seguridad (CONNECT sin token o con
 * token revocado, suscripciones a colas o tópicos ajenos) viajan por Redis ({@value #CHANNEL}) y el
 * Wellbeing Service los inserta en su {@code security_audit}. La misma traza queda en el log.
 */
@Component
public class GatewaySecurityAudit {

    public static final String CHANNEL = "zeal.security.audit";
    private static final Logger log = LoggerFactory.getLogger("zeal.security.audit");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String service;

    public GatewaySecurityAudit(StringRedisTemplate redis, ObjectMapper objectMapper,
                                @Value("${spring.application.name}") String service) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.service = service;
    }

    public void publish(String eventType, String outcome, String userId, StompHeaderAccessor accessor, String detail) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Map<String, String> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString()); // Lo inserta una sola réplica de wellbeing
        event.put("service", service);
        event.put("eventType", eventType);
        event.put("outcome", outcome);
        event.put("userId", userId);
        event.put("ip", attributes == null ? null : (String) attributes.get(ClientIpHandshakeInterceptor.CLIENT_IP));
        event.put("correlationId", attributes == null ? null : (String) attributes.get(ClientIpHandshakeInterceptor.CORRELATION_ID));
        event.put("detail", detail);
        log.info("security_audit service={} event={} outcome={} userId={} ip={} detail=\"{}\"", service, eventType,
                outcome, userId, event.get("ip"), detail);
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("No se pudo enviar el evento de auditoría: {}", e.getMessage());
        }
    }
}
