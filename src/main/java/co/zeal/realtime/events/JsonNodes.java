package co.zeal.realtime.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.StreamSupport;

/** Lectura estricta de campos obligatorios de un evento. */
public final class JsonNodes {

    private JsonNodes() {
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("El evento no trae el campo de texto '" + field + "'");
        }
        return value.asText();
    }

    public static JsonNode object(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("El evento no trae el objeto '" + field + "'");
        }
        return value;
    }

    public static List<String> texts(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("El evento no trae la lista '" + field + "'");
        }
        return StreamSupport.stream(value.spliterator(), false).map(JsonNode::asText).toList();
    }
}
