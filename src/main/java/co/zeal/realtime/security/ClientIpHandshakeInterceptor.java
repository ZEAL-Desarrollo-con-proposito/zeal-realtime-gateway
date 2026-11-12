package co.zeal.realtime.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Guarda en la sesión WebSocket la IP del cliente (ya corregida por {@code forward-headers-strategy}
 * detrás de NGINX) y el correlationId del handshake, para la auditoría de eventos STOMP.
 */
public class ClientIpHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CLIENT_IP = "zeal.clientIp";
    public static final String CORRELATION_ID = "zeal.correlationId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servlet) {
            attributes.put(CLIENT_IP, servlet.getServletRequest().getRemoteAddr());
            String correlationId = servlet.getServletRequest().getHeader("X-Request-Id");
            if (correlationId != null) {
                attributes.put(CORRELATION_ID, correlationId);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // Nada que hacer.
    }
}
