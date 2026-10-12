package co.zeal.realtime.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj y zona horaria del negocio inyectables: las reglas de tiempo (ventanas de 24 h, "el
 * mismo día") son testeables y no dependen de la zona del servidor.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId businessZone(@Value("${app.time-zone}") String zone) {
        return ZoneId.of(zone);
    }
}
