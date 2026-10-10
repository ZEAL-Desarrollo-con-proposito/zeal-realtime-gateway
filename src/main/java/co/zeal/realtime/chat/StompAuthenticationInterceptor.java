package co.zeal.realtime.chat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    @Value("${app.jwt.secret}")
    private String secret;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessagingException("Se requiere autenticación para conectar al chat.");
        }
        String token = authorization.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
                throw new MessagingException("El token no identifica a un usuario y su rol.");
            }
            accessor.setUser(new RealtimePrincipal(userId, role, token));
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (MessagingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MessagingException("Token inválido o expirado.", exception);
        }
    }
}
