package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.AuthenticatedUser;

import java.security.Principal;

/**
 * Identidad de una sesión STOMP. {@link #getName()} es el id de la persona: así
 * {@code convertAndSendToUser(userId, ...)} llega a todas sus pestañas. Conserva el token para
 * consultar al Wellbeing Service en su nombre.
 */
public record StompPrincipal(AuthenticatedUser user, String accessToken) implements Principal {

    @Override
    public String getName() {
        return user.id();
    }

    @Override
    public String toString() {
        return "StompPrincipal[" + user.id() + "]"; // Nunca imprime el token.
    }
}
