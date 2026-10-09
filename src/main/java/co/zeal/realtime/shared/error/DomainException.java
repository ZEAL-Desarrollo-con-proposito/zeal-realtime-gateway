package co.zeal.realtime.shared.error;

/**
 * Violación de una regla del dominio. Las subclases expresan la categoría del fallo; la capa web
 * decide qué código HTTP le corresponde, así el dominio no depende de HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
