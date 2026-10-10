package co.zeal.realtime.chat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StompAuthenticationInterceptorTest {

    private static final String SECRET =
            "ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!";
    private static final MessageChannel CHANNEL = (message, timeout) -> true;

    @Test
    void authenticatesConnectAndReturnsTheMessageWithItsUserPrincipal() {
        StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", SECRET);

        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        String bearerToken = token("employee-1", "EMPLEADO");
        headers.setNativeHeader("Authorization", bearerToken);
        Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(connect, CHANNEL);
        RealtimePrincipal principal = (RealtimePrincipal) StompHeaderAccessor.wrap(authenticated).getUser();

        assertEquals("employee-1", principal.getName());
        assertEquals("EMPLEADO", principal.role());
        assertEquals(bearerToken.substring(7), principal.accessToken());
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", SECRET);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect, CHANNEL));
    }

    private String token(String userId, String role) {
        return "Bearer " + Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
