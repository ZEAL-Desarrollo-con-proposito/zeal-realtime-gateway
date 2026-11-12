package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.AuthenticatedUser;

import java.security.Principal;

/**
 * Identidad de una sesión STOMP. {@link #getName()} es el id de la persona: así
 * {@code convertAndSendToUser(userId, ...)} llega a todas sus pestañas. Conserva el token para
 * consultar al Wellbeing Service en su nombre, y su sesión ({@code sid}) y {@code jti} para cerrar la
 * conexión en cuanto se revoquen (R2-13).
 */
public record StompPrincipal(AuthenticatedUser user, String accessToken, String sessionId, String tokenId)
        implements Principal {

    public StompPrincipal(AuthenticatedUser user, String accessToken) {
        this(user, accessToken, null, null);
    }

    @Override
    public String getName() {
        return user.id();
    }

    public boolean isCoveredBy(String revokedSessionId, String revokedTokenId) {
        return (revokedSessionId != null && revokedSessionId.equals(sessionId))
                || (revokedTokenId != null && revokedTokenId.equals(tokenId));
    }

    @Override
    public String toString() {
        return "StompPrincipal[" + user.id() + "]"; // Nunca imprime el token.
    }
}
