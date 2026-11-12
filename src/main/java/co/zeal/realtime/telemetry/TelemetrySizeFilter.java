package co.zeal.realtime.telemetry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** R2-32 · Rechaza (413) cuerpos de telemetría grandes o sin longitud declarada, antes de leerlos. */
@Component
public class TelemetrySizeFilter extends OncePerRequestFilter {

    private final TelemetryProperties properties;

    public TelemetrySizeFilter(TelemetryProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/telemetria".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0 || length > properties.maxBodyBytes()) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":413,\"error\":\"Payload Too Large\",\"message\":\"El envío de "
                    + "telemetría supera el tamaño permitido.\",\"path\":\"/api/telemetria\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
