package co.zeal.realtime.delivery;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Mensaje para colas privadas que viaja entre réplicas por Redis. Se dirige a personas concretas
 * ({@code userIds}) o a los administradores conectados de una empresa ({@code adminsOf}).
 */
public record UserDelivery(List<String> userIds, String adminsOf, String queue, JsonNode payload) {

    public static UserDelivery toUsers(List<String> userIds, String queue, JsonNode payload) {
        return new UserDelivery(List.copyOf(userIds), null, queue, payload);
    }

    public static UserDelivery toAdminsOf(String organization, String queue, JsonNode payload) {
        return new UserDelivery(List.of(), organization, queue, payload);
    }
}
