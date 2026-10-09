package co.zeal.realtime.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Suscribe el despachador exactamente a los canales que sabe manejar. */
@Configuration
public class RedisListenerConfig {

    @Bean
    RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory connectionFactory,
                                                         RedisEventDispatcher dispatcher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(dispatcher, dispatcher.channels().stream().map(ChannelTopic::new).toList());
        return container;
    }
}
