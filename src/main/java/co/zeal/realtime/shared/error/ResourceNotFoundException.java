package co.zeal.realtime.shared.error;

/** El recurso no existe o quien lo pide no tiene derecho a saber que existe. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
