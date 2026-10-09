package co.zeal.realtime.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Construye {@link ApiError} con la hora del reloj de la aplicación. */
@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(HttpStatus status, String message, HttpServletRequest request) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI(),
                Instant.now(clock));
    }
}
