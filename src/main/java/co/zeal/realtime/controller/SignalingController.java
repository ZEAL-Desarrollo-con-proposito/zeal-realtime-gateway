package co.zeal.realtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class SignalingController {

    private static final Logger log = LoggerFactory.getLogger(SignalingController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public SignalingController(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * RT-3: Señalización WebRTC 1:1
     * Cliente envía a: /app/salas/{reservationId}/senales
     * Servidor reenvía a: /topic/sala.{reservationId}
     *
     * Señales: OFERTA, RESPUESTA, CANDIDATO, LISTO, MEDIOS, SALIO
     * Envuelto en RoomEvent: { type: 'SENAL', reservationId, signal }
     */
    @MessageMapping("/salas/{reservationId}/senales")
    public void handleSignal(
            @DestinationVariable String reservationId,
            @Payload Map<String, Object> signal,
            Principal sender) {

        log.debug("[WebRTC] Señal de {} para sala {}: {}", sender.getName(), reservationId, signal.get("type"));

        Map<String, Object> roomEvent = Map.of(
                "type", "SENAL",
                "reservationId", reservationId,
                "signal", signal
        );

        messagingTemplate.convertAndSend("/topic/sala." + reservationId, roomEvent);
        log.debug("[WebRTC] Señal reenviada a /topic/sala.{}", reservationId);
    }
}
