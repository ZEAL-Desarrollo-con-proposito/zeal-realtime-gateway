package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.web.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP sobre SockJS en {@code /ws}. SockJS ofrece el fallback a HTTP streaming/long polling
 * cuando el WebSocket no está disponible (RNF-07) y los heartbeats detectan conexiones caídas.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;
    private static final int MAX_MESSAGE_BYTES = 128 * 1024; // Una oferta SDP cabe con holgura.

    private final CorsProperties cors;
    private final StompAuthenticationInterceptor authentication;
    private final StompSubscriptionGuard subscriptionGuard;

    public WebSocketConfig(CorsProperties cors, StompAuthenticationInterceptor authentication,
                           StompSubscriptionGuard subscriptionGuard) {
        this.cors = cors;
        this.authentication = authentication;
        this.subscriptionGuard = subscriptionGuard;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(cors.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authentication, subscriptionGuard);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_MESSAGE_BYTES);
    }

    @Bean
    ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
