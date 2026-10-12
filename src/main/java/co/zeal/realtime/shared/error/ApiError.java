package co.zeal.realtime.shared.error;

import java.time.Instant;

/**
 * Cuerpo de error uniforme de la API. El frontend muestra {@code message} al usuario, por eso
 * siempre es un texto en español pensado para personas, nunca un detalle técnico.
 */
public record ApiError(int status, String error, String message, String path, Instant timestamp) {
}
