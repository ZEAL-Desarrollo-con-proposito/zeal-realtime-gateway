package co.zeal.realtime.shared.web;

import co.zeal.realtime.shared.security.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Bean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    /** R2-29: el id interno de quien llama en cada línea de log de la petición. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                CorrelationIdFilter.tagCurrentUser();
                return true;
            }
        });
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * Lo usa Spring Security, así el preflight se responde antes de exigir el token. El filtro CORS
     * de Security responde también {@code /ws/**}, y el cliente SockJS del navegador envía sus
     * peticiones HTTP con {@code withCredentials}: esa ruta exige {@code Allow-Credentials: true}.
     * Se registra primero porque gana el primer patrón que coincide.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOriginPatterns(properties.allowedOrigins());
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        api.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        api.setMaxAge(Duration.ofHours(1));

        CorsConfiguration sockJs = new CorsConfiguration(api);
        sockJs.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        sockJs.setAllowedHeaders(List.of("*"));
        sockJs.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", sockJs);
        source.registerCorsConfiguration("/**", api);
        return source;
    }
}
