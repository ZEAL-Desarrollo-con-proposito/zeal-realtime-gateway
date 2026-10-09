package co.zeal.realtime.events.handlers;

import co.zeal.realtime.events.Channels;
import co.zeal.realtime.events.IntegrationEventHandler;
import co.zeal.realtime.events.JsonNodes;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/** HU-16 (RF-25): contador de cupos en vivo, por convocatoria y por taller ({@code type: CUPOS}). */
@Configuration
class SpotsChangedHandlers {

    @Bean
    IntegrationEventHandler projectSpotsHandler(RealtimeSender sender) {
        return handler(Channels.PROJECT_SPOTS, "projectId", Destinations::projectSpotsTopic, sender);
    }

    @Bean
    IntegrationEventHandler workshopSpotsHandler(RealtimeSender sender) {
        return handler(Channels.WORKSHOP_SPOTS, "workshopId", Destinations::workshopSpotsTopic, sender);
    }

    private static IntegrationEventHandler handler(String channel, String idField, Function<String, String> topic,
                                                   RealtimeSender sender) {
        return new IntegrationEventHandler() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public void handle(JsonNode event) {
                ObjectNode spots = event.deepCopy();
                spots.put("type", "CUPOS");
                sender.toTopic(topic.apply(JsonNodes.text(event, idField)), spots);
            }
        };
    }
}
