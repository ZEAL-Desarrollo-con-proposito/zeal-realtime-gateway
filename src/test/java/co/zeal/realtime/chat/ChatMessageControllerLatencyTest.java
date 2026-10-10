package co.zeal.realtime.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageControllerLatencyTest {

    @Test
    void persistsAndDeliversPrivatelyToBothParticipantsWithin500Milliseconds() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicInteger sequence = new AtomicInteger();
        HttpServer marketplace = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        marketplace.createContext("/api/chats/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String body = objectMapper.writeValueAsString(new ChatHistoryClient.ChatMessageResponse(
                    "message-" + sequence.incrementAndGet(),
                    "employee-1:psychologist-1",
                    "employee-1",
                    "Empleado Uno",
                    "Mensaje de prueba",
                    Instant.now()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length);
            try (var response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        });
        marketplace.start();

        List<Message<?>> deliveries = new CopyOnWriteArrayList<>();
        MessageChannel channel = (message, timeout) -> {
            deliveries.add(message);
            return true;
        };
        SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(channel);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        messagingTemplate.setMessageConverter(converter);
        ChatHistoryClient historyClient = new ChatHistoryClient(
                objectMapper, "http://127.0.0.1:" + marketplace.getAddress().getPort());
        ChatMessageController controller = new ChatMessageController(historyClient, messagingTemplate);
        RealtimePrincipal principal = new RealtimePrincipal("employee-1", "EMPLEADO", "test-token");

        try {
            for (int index = 0; index < 10; index++) {
                long startedAt = System.nanoTime();
                controller.send("employee-1%3Apsychologist-1",
                        new ChatMessageController.OutgoingChatMessage("Mensaje de prueba"), principal);
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
                assertTrue(elapsedMillis < 500,
                        "Persistencia y entrega STOMP privada tardaron " + elapsedMillis + " ms");
            }
            assertEquals(20, deliveries.size());
            List<String> destinations = deliveries.stream()
                    .map(message -> SimpMessageHeaderAccessor.getDestination(message.getHeaders()))
                    .toList();
            assertEquals(10, destinations.stream().filter("/user/employee-1/queue/chats"::equals).count());
            assertEquals(10, destinations.stream().filter("/user/psychologist-1/queue/chats"::equals).count());
            assertTrue(deliveries.stream().allMatch(message -> {
                Object payload = message.getPayload();
                return payload instanceof byte[] bytes
                        && new String(bytes, StandardCharsets.UTF_8).contains("\"type\":\"NUEVO\"");
            }));
        } finally {
            marketplace.stop(0);
        }
    }
}
