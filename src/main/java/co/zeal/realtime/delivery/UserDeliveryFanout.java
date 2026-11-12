package co.zeal.realtime.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica una entrega privada para que la hagan todas las réplicas (HU-DT-01).
 *
 * <p>Una persona puede tener pestañas en réplicas distintas, y cada réplica solo conoce sus propias
 * sesiones (Spring resuelve {@code /user/queue/x} a {@code /queue/x-user{sesión}} con el registro
 * local). Por eso toda entrega privada se publica en {@value #CHANNEL} y cada réplica la entrega a
 * sus sesiones locales ({@link LocalUserDelivery}). Así no hace falta replicar el registro de
 * sesiones ni guardar estado de otras réplicas.
 */
@Component
public class UserDeliveryFanout {

    public static final String CHANNEL = "zeal.gateway.user-deliveries";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public UserDeliveryFanout(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(UserDelivery delivery) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(delivery));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la entrega privada", e);
        }
    }
}
