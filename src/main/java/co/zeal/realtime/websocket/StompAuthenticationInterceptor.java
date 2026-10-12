package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Reto RT-1/RT-2 (privacidad): el frame CONNECT debe traer {@code Authorization: Bearer <jwt>}.
 * Sin un token válido la conexión se rechaza; con él, la sesión queda asociada a la persona y
 * las colas {@code /user/queue/...} le llegan solo a ella.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthenticationInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public StompAuthenticationInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new BadCredentialsException("La conexión en tiempo real requiere un token");
        }
        String token = header.substring(BEARER.length()).trim();
        try {
            AuthenticatedUser user = AuthenticatedUser.from(jwtDecoder.decode(token));
            accessor.setUser(new StompPrincipal(user, token));
            return message;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("CONNECT rechazado: {}", e.getMessage());
            throw new BadCredentialsException("Token inválido o expirado", e);
        }
    }
}
