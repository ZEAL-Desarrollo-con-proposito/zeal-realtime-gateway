package co.zeal.realtime.events;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Traduce un evento de dominio de un canal Redis a mensajes WebSocket. Para soportar un evento
 * nuevo basta con agregar un handler: el despachador no cambia (principio abierto/cerrado).
 */
public interface IntegrationEventHandler {

    String channel();

    void handle(JsonNode event);
}
