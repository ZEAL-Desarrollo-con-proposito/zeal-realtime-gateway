package co.zeal.realtime.signaling;

import co.zeal.realtime.wellbeing.ParticipantAccess;
import co.zeal.realtime.websocket.Destinations;
import co.zeal.realtime.websocket.RealtimeSender;
import co.zeal.realtime.websocket.StompPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Reto RT-3: servidor de señalización WebRTC 1:1. Reenvía ofertas, respuestas y candidatos ICE
 * únicamente a la otra persona de la sesión, por su cola privada, y solo con la sala activa.
 */
@Controller
public class SignalingController {

    static final Set<String> SIGNAL_TYPES = Set.of("LISTO", "OFERTA", "RESPUESTA", "CANDIDATO", "MEDIOS", "SALIO");
    private static final Logger log = LoggerFactory.getLogger(SignalingController.class);

    private final ParticipantAccess access;
    private final RealtimeSender sender;
    private final Clock clock;

    public SignalingController(ParticipantAccess access, RealtimeSender sender, Clock clock) {
        this.access = access;
        this.sender = sender;
        this.clock = clock;
    }

    @MessageMapping("/salas/{reservationId}/senales")
    public void relaySignal(@DestinationVariable("reservationId") String encodedReservationId,
                            @Payload Map<String, Object> signal, StompPrincipal principal) {
        String reservationId = Destinations.decode(encodedReservationId);
        if (!SIGNAL_TYPES.contains(String.valueOf(signal.get("type")))) {
            log.debug("Señal descartada con tipo desconocido en la sala {}", reservationId);
            return;
        }
        Instant now = Instant.now(clock);
        access.room(principal, reservationId)
                .filter(room -> room.acceptsSignalsAt(now))
                .ifPresentOrElse(
                        room -> sender.toUser(room.otherParticipantId(), Destinations.ROOMS_QUEUE,
                                Map.of("type", "SENAL", "reservationId", room.reservationId(), "signal", signal)),
                        () -> log.debug("Señal rechazada: {} no tiene la sala {} activa", principal.getName(),
                                reservationId));
    }
}
