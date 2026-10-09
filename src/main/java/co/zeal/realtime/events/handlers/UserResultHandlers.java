package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** HU-41: el resultado de postularse o inscribirse llega a todas las pestañas de quien lo hizo. */
@Configuration
class UserResultHandlers {

    @Bean
    IntegrationEventHandler applicationResultHandler(RealtimeSender sender) {
        return handler(Channels.APPLICATIONS, "POSTULACION", "application", Destinations.APPLICATIONS_QUEUE, sender);
    }

    @Bean
    IntegrationEventHandler enrollmentResultHandler(RealtimeSender sender) {
        return handler(Channels.ENROLLMENTS, "INSCRIPCION", "enrollment", Destinations.ENROLLMENTS_QUEUE, sender);
    }

    private static IntegrationEventHandler handler(String channel, String type, String field, String queue,
                                                   RealtimeSender sender) {
        return new IntegrationEventHandler() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public void handle(JsonNode event) {
                sender.toUser(JsonNodes.text(event, "userId"), queue,
                        Map.of("type", type, field, JsonNodes.object(event, field)));
            }
        };
    }
}
