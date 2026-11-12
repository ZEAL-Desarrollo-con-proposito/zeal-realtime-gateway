package co.zeal.realtime.security;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sockets abiertos en ESTA réplica, para poder cerrarlos al revocarse su token (R2-13). Es estado
 * ligado a la conexión física (el socket solo existe en la réplica que lo atiende), no estado de
 * negocio: se borra al cerrarse cada conexión.
 */
@Component
public class LiveSockets implements WebSocketHandlerDecoratorFactory {

    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sockets.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** @return {@code true} si la sesión estaba abierta aquí y se cerró. */
    public boolean close(String sessionId, CloseStatus status) {
        WebSocketSession session = sockets.get(sessionId);
        if (session == null) {
            return false;
        }
        try {
            session.close(status);
        } catch (IOException e) {
            sockets.remove(sessionId);
        }
        return true;
    }
}
