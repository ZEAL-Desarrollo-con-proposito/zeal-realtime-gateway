package co.zeal.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.zeal.realtime.chat.StompAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor authenticationInterceptor;

    public WebSocketConfig(StompAuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().findAndRegisterModules());
        messageConverters.add(converter);
        return false;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // El frontend conecta a http://localhost:8083/ws — debe coincidir exactamente
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:3000")
                .withSockJS();
    }
}
