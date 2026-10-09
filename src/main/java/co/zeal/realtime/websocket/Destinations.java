package co.zeal.realtime.websocket;

import org.springframework.web.util.UriUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Destinos STOMP que consume el frontend. Los segmentos variables se codifican igual que
 * {@code encodeURIComponent} de JavaScript, porque así se suscribe el cliente.
 */
public final class Destinations {

    public static final String SLOTS_TOPIC = "/topic/franjas";
    public static final String NOTIFICATIONS_QUEUE = "/queue/notificaciones";
    public static final String FREED_SLOTS_QUEUE = "/queue/cupos-liberados";
    public static final String ROOMS_QUEUE = "/queue/salas";
    public static final String CHATS_QUEUE = "/queue/chats";
    public static final String ADMIN_PROJECTS_QUEUE = "/queue/proyectos";
    public static final String ADMIN_WORKSHOPS_QUEUE = "/queue/talleres";
    public static final String APPLICATIONS_QUEUE = "/queue/postulaciones";
    public static final String ENROLLMENTS_QUEUE = "/queue/inscripciones";

    private Destinations() {
    }

    public static String presenceTopic(String userId) {
        return "/topic/presencia/" + encode(userId);
    }

    public static String organizationProjectsTopic(String organization) {
        return "/topic/organizaciones/" + encode(organization) + "/proyectos";
    }

    public static String organizationWorkshopsTopic(String organization) {
        return "/topic/organizaciones/" + encode(organization) + "/talleres";
    }

    public static String projectSpotsTopic(String projectId) {
        return "/topic/proyectos/" + encode(projectId) + "/cupos";
    }

    public static String workshopSpotsTopic(String workshopId) {
        return "/topic/talleres/" + encode(workshopId) + "/cupos";
    }

    /**
     * Las variables de un destino {@code /app/...} llegan tal como las codificó el cliente con
     * {@code encodeURIComponent}; Spring no las decodifica.
     */
    public static String decode(String segment) {
        return UriUtils.decode(segment, StandardCharsets.UTF_8);
    }

    /** Equivalente de {@code encodeURIComponent}: deja sin codificar {@code A-Z a-z 0-9 - _ . ! ~ * ' ( )}. */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
