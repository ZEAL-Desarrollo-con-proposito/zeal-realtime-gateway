package co.zeal.realtime.events;

import co.zeal.realtime.delivery.LocalUserDelivery;
import co.zeal.realtime.delivery.UserDeliveryFanout;
import co.zeal.realtime.security.RevokedSessionCloser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Suscribe el despachador exactamente a los canales que sabe manejar, y la entrega local al canal
 * interno de colas privadas.
 *
 * <p>Los mensajes se procesan en un único hilo, en el orden en que llegan: con el ejecutor por
 * defecto (un hilo por mensaje) una señal WebRTC o un contador de cupos podría adelantar a otro
 * anterior.
 */
@Configuration
public class RedisListenerConfig {

    @Bean
    RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory connectionFactory,
                                                         RedisEventDispatcher dispatcher,
                                                         LocalUserDelivery localDelivery,
                                                         RevokedSessionCloser revokedSessions) {
        // No se expone como bean: un Executor propio desactivaría el applicationTaskExecutor de Boot.
        ThreadPoolTaskExecutor orderedExecutor = new ThreadPoolTaskExecutor();
        orderedExecutor.setCorePoolSize(1);
        orderedExecutor.setMaxPoolSize(1);
        orderedExecutor.setDaemon(true);
        orderedExecutor.setThreadNamePrefix("redis-events-");
        orderedExecutor.initialize();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(orderedExecutor);
        container.addMessageListener(dispatcher, dispatcher.channels().stream().map(ChannelTopic::new).toList());
        container.addMessageListener(localDelivery, new ChannelTopic(UserDeliveryFanout.CHANNEL));
        container.addMessageListener(revokedSessions, new ChannelTopic(RevokedSessionCloser.CHANNEL));
        return container;
    }
}
