package co.zeal.realtime.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Consumidor EDA: entrega cada mensaje del broker al handler de su canal. */
@Component
public class RedisEventDispatcher implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventDispatcher.class);

    private final Map<String, IntegrationEventHandler> handlers;
    private final ObjectMapper objectMapper;

    public RedisEventDispatcher(List<IntegrationEventHandler> handlers, ObjectMapper objectMapper) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(IntegrationEventHandler::channel, Function.identity()));
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        IntegrationEventHandler handler = handlers.get(channel);
        if (handler == null) {
            log.debug("Canal sin handler: {}", channel);
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            handler.handle(event);
        } catch (Exception e) {
            // Un evento malformado no debe detener al consumidor ni afectar a los siguientes.
            log.error("No se pudo procesar el evento del canal {}", channel, e);
        }
    }

    public Set<String> channels() {
        return handlers.keySet();
    }
}
