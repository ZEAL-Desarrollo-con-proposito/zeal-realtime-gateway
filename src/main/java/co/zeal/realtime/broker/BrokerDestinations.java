package co.zeal.realtime.broker;

/**
 * Traduce los destinos STOMP del frontend a la sintaxis de RabbitMQ y de vuelta.
 *
 * <p>RabbitMQ solo acepta destinos {@code /topic/<routing key>} cuya clave no contiene {@code /}:
 * los niveles de una clave se separan con punto y {@code *}/{@code #} son comodines. El frontend
 * conserva sus nombres ({@code /topic/organizaciones/{empresa}/proyectos}); el gateway los convierte
 * en {@code /topic/organizaciones.{empresa}.proyectos} al hablar con el broker. Dentro de cada
 * segmento, {@code .}, {@code *} y {@code #} se escapan como {@code %2E}, {@code %2A} y {@code %23}
 * para que el contenido de un segmento nunca cambie la estructura de la clave ni actúe como comodín
 * (sin esto, suscribirse a {@code /topic/presencia/*} escucharía la presencia de todos).
 *
 * <p>Las colas {@code /queue/...} no cambian: RabbitMQ las acepta tal cual.
 */
public final class BrokerDestinations {

    private static final String TOPIC = "/topic/";

    private BrokerDestinations() {
    }

    public static String toBroker(String destination) {
        if (destination == null || !destination.startsWith(TOPIC)) {
            return destination;
        }
        String[] segments = destination.substring(TOPIC.length()).split("/", -1);
        StringBuilder key = new StringBuilder(TOPIC);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                key.append('.');
            }
            key.append(segments[i].replace(".", "%2E").replace("*", "%2A").replace("#", "%23"));
        }
        return key.toString();
    }

    /**
     * Inversa de {@link #toBroker}. RabbitMQ además escapa {@code %} como {@code %25} en la cabecera
     * {@code destination} de los MESSAGE, así que primero se deshace ese escape.
     */
    public static String fromBroker(String destination) {
        if (destination == null || !destination.startsWith(TOPIC)) {
            return destination;
        }
        String key = destination.substring(TOPIC.length()).replace("%25", "%");
        String[] segments = key.split("\\.", -1);
        StringBuilder path = new StringBuilder(TOPIC);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                path.append('/');
            }
            path.append(segments[i].replace("%2E", ".").replace("%2A", "*").replace("%23", "#"));
        }
        return path.toString();
    }
}
