package co.zeal.realtime.telemetry;

import co.zeal.realtime.shared.security.AuthenticatedUser;
import co.zeal.realtime.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** R2-32 · Telemetría del frontend: errores, uso por funcionalidad y latencias percibidas. */
@RestController
public class TelemetryController {

    private final TelemetryService telemetry;

    public TelemetryController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @PostMapping("/api/telemetria")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receive(@CurrentUser AuthenticatedUser user, @Valid @RequestBody TelemetryBatch batch) {
        telemetry.record(user.id(), batch);
    }
}
