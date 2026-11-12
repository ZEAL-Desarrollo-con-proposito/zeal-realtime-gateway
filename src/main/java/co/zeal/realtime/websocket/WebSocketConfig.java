package co.zeal.realtime.websocket;

import co.zeal.realtime.broker.BrokerDestinationInterceptor;
import co.zeal.realtime.broker.BrokerOutboundInterceptor;
import co.zeal.realtime.broker.BrokerRelayProperties;
import co.zeal.realtime.presence.PresenceHeartbeatInterceptor;
import co.zeal.realtime.security.ClientIpHandshakeInterceptor;
import co.zeal.realtime.security.LiveSockets;
import co.zeal.realtime.shared.web.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP sobre SockJS en {@code /ws}. SockJS ofrece el fallback a HTTP streaming/long polling
 * cuando el WebSocket no está disponible (RNF-07).
 *
 * <p>HU-DT-01 (ADR-04): el broker ya no vive en memoria. {@code /topic} y {@code /queue} se
 * delegan a RabbitMQ (plugin STOMP), que comparten todas las réplicas: un mensaje publicado en
 * cualquier réplica llega a los suscriptores de todas. Los latidos del navegador viajan hasta
 * RabbitMQ, que cierra la sesión si dejan de llegar.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long SYSTEM_HEARTBEAT_MS = 10_000;
    private static final int MAX_MESSAGE_BYTES = 128 * 1024; // Una oferta SDP cabe con holgura.

    private final CorsProperties cors;
    private final BrokerRelayProperties relay;
    private final StompAuthenticationInterceptor authentication;
    private final StompSubscriptionGuard subscriptionGuard;
    private final PresenceHeartbeatInterceptor presenceHeartbeat;
    private final WebSocketThreadProperties threads;
    private final LiveSockets liveSockets;

    public WebSocketConfig(CorsProperties cors, BrokerRelayProperties relay,
                           StompAuthenticationInterceptor authentication, StompSubscriptionGuard subscriptionGuard,
                           PresenceHeartbeatInterceptor presenceHeartbeat, WebSocketThreadProperties threads,
                           LiveSockets liveSockets) {
        this.liveSockets = liveSockets;
        this.cors = cors;
        this.relay = relay;
        this.authentication = authentication;
        this.subscriptionGuard = subscriptionGuard;
        this.presenceHeartbeat = presenceHeartbeat;
        this.threads = threads;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(cors.allowedOrigins().toArray(String[]::new))
                .addInterceptors(new ClientIpHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic", "/queue", BrokerDestinationInterceptor.EXISTING_QUEUE)
                .setRelayHost(relay.host())
                .setRelayPort(relay.port())
                .setVirtualHost(relay.virtualHost())
                .setClientLogin(relay.clientLogin())
                .setClientPasscode(relay.clientPasscode())
                .setSystemLogin(relay.systemLogin())
                .setSystemPasscode(relay.systemPasscode())
                .setSystemHeartbeatSendInterval(SYSTEM_HEARTBEAT_MS)
                .setSystemHeartbeatReceiveInterval(SYSTEM_HEARTBEAT_MS);
        // Lo que publica el servidor (tópicos y colas privadas resueltas) también se traduce.
        registry.configureBrokerChannel().interceptors(new BrokerDestinationInterceptor());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // Sin userDestinationBroadcast ni userRegistryBroadcast a propósito: cada réplica entrega
        // las colas privadas solo a sus sesiones locales y el reparto entre réplicas lo hace
        // UserDeliveryFanout por Redis (ver ese componente).
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // El orden importa: autenticar, autorizar con los nombres del contrato, renovar presencia
        // y por último traducir al dialecto de RabbitMQ.
        registration.interceptors(authentication, subscriptionGuard, presenceHeartbeat,
                new BrokerDestinationInterceptor(), new StompMdcInterceptor());
        registration.taskExecutor().corePoolSize(threads.inbound()).maxPoolSize(threads.inbound());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new BrokerOutboundInterceptor());
        registration.taskExecutor().corePoolSize(threads.outbound()).maxPoolSize(threads.outbound());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_MESSAGE_BYTES);
        registration.addDecoratorFactory(liveSockets); // Para cerrar sockets de tokens revocados (R2-13)
    }
}
