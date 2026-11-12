package co.zeal.realtime.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Contrato de {@code POST /api/telemetria} (docs/contratos/telemetria.md). Sin campos de texto libre. */
public record TelemetryBatch(@NotEmpty(message = "Envía al menos un evento") @Size(max = 20,
        message = "Máximo 20 eventos por envío") List<@Valid @NotNull Event> events) {

    public enum Type { error, uso, latencia }

    public record Event(
            @NotNull(message = "El tipo es obligatorio") Type type,
            @NotNull @Pattern(regexp = "^[a-z0-9_]{1,40}$", message = "Nombre inválido") String name,
            // Ruta del SPA sin identificadores (p. ej. /empleado/mis-citas): solo minúsculas y guiones.
            @Pattern(regexp = "^(/[a-z-]{1,30}){0,4}/?$", message = "Ruta inválida") String route,
            @Min(0) @Max(120_000) Long valueMs) {
    }
}
