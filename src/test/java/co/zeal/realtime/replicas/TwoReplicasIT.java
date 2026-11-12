package co.zeal.realtime.replicas;

import co.zeal.realtime.RealtimeGatewayApplication;
import co.zeal.realtime.presence.PresenceRegistry;
import co.zeal.realtime.support.Infrastructure;
import co.zeal.realtime.support.StompTestClient;
import co.zeal.realtime.support.Tokens;
import co.zeal.realtime.wellbeing.WellbeingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static co.zeal.realtime.support.StompTestClient.subscribe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * HU-DT-01 / DT-4: dos réplicas reales del gateway (dos contextos de Spring con su propio puerto)
 * sobre el mismo Redis y el mismo RabbitMQ. Cada cliente se conecta, forzado, a una réplica
 * distinta y se verifica que se ven en línea, chatean y reciben los avisos de cupos, franjas y
 * notificaciones exactamente una vez.
 */
class TwoReplicasIT {

    static final WellbeingClient WELLBEING = Mockito.mock(WellbeingClient.class);
    static ConfigurableApplicationContext replicaA;
    static ConfigurableApplicationContext replicaB;
    static int portA;
    static int portB;

    final StompTestClient clients = new StompTestClient();
    final ObjectMapper objectMapper = new ObjectMapper();
    StringRedisTemplate redis;

    @Configuration
    static class StubWellbeing {
        @Bean
        @Primary
        WellbeingClient stubWellbeingClient() {
            return WELLBEING;
        }
    }

    @BeforeAll
    static void startReplicas() {
        replicaA = startReplica("replica-a");
        replicaB = startReplica("replica-b");
        portA = port(replicaA);
        portB = port(replicaB);
    }

    @AfterAll
    static void stopReplicas() {
        replicaA.close();
        replicaB.close();
    }

    @BeforeEach
    void resetState() {
        redis = replicaA.getBean(StringRedisTemplate.class);
        Mockito.reset(WELLBEING);
    }

    @AfterEach
    void disconnect() {
        clients.close();
    }

    @Test
    void usersOnDifferentReplicasSeeEachOtherOnlineAndOffline() throws Exception {
        StompSession laura = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> presence = subscribe(laura, "/topic/presencia/usr-empleado-001");

        StompSession david = clients.connect(portA, Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S."));

        assertThat(presence.poll(5, TimeUnit.SECONDS)).containsEntry("status", "online");
        assertThat(presenceViaRest(portB, "usr-empleado-001")).contains("\"online\"");

        david.disconnect();

        assertThat(presence.poll(5, TimeUnit.SECONDS)).containsEntry("status", "offline");
        assertThat(presenceViaRest(portA, "usr-empleado-001")).contains("\"offline\"");
    }

    @Test
    void aUserStaysOnlineWhileATabRemainsOnAnyReplica() throws Exception {
        StompSession laura = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> presence = subscribe(laura, "/topic/presencia/usr-empleado-002");
        String token = Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S.");
        StompSession tabOnA = clients.connect(portA, token);
        assertThat(presence.poll(5, TimeUnit.SECONDS)).containsEntry("status", "online");
        StompSession tabOnB = clients.connect(portB, token);

        tabOnA.disconnect();

        assertThat(presence.poll(1500, TimeUnit.MILLISECONDS)).as("sigue conectada en la réplica B").isNull();
        assertThat(presenceViaRest(portA, "usr-empleado-002")).contains("\"online\"");
        tabOnB.disconnect();
        assertThat(presence.poll(5, TimeUnit.SECONDS)).containsEntry("status", "offline");
    }

    @Test
    void connectionsOfACrashedReplicaExpireAndAreAnnouncedOffline() throws Exception {
        StompSession laura = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> presence = subscribe(laura, "/topic/presencia/usr-fantasma");

        // Una réplica registró la conexión y "murió": nadie la renueva ni envía el DISCONNECT.
        replicaA.getBean(PresenceRegistry.class).touch("usr-fantasma", "sesion-de-replica-caida");

        assertThat(presence.poll(10, TimeUnit.SECONDS)).as("el barrido la retira al vencer el TTL")
                .containsEntry("status", "offline");
        assertThat(presence.poll(2, TimeUnit.SECONDS)).as("se anuncia una sola vez").isNull();
    }

    @Test
    void heartbeatsKeepAConnectionAliveBeyondItsTtl() throws Exception {
        StompSession laura = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> presence = subscribe(laura, "/topic/presencia/usr-empleado-004");
        // TTL de 3 s en estas réplicas: latidos cada segundo la mantienen viva.
        clients.connect(portA, Tokens.token("usr-empleado-004", "EMPLEADO", "Nexura S.A.S."), 1_000);
        assertThat(presence.poll(5, TimeUnit.SECONDS)).containsEntry("status", "online");

        assertThat(presence.poll(7, TimeUnit.SECONDS)).as("no se marca desconectada mientras late").isNull();
        assertThat(presenceViaRest(portB, "usr-empleado-004")).contains("\"online\"");
    }

    @Test
    void chatCrossesReplicasAndEchoesToTheSendersOtherTabs() throws Exception {
        String davidToken = Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.");
        StompSession davidOnA = clients.connect(portA, davidToken);
        StompSession davidOnB = clients.connect(portB, davidToken);
        StompSession lauraOnB = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> lauraInbox = subscribe(lauraOnB, "/user/queue/chats");
        BlockingQueue<Map<String, Object>> davidOtherTab = subscribe(davidOnB, "/user/queue/chats");
        String conversationId = "usr-empleado-001:usr-psicologo-001";
        when(WELLBEING.conversation(eq(davidToken), eq(conversationId))).thenReturn(Optional.of(
                new WellbeingClient.ConversationAccess(conversationId, "usr-empleado-001", "usr-psicologo-001")));
        when(WELLBEING.sendChatMessage(eq(davidToken), eq(conversationId), eq("Hola"))).thenReturn(Optional.of(
                Map.of("id", "mensaje-1", "conversationId", conversationId, "text", "Hola")));

        davidOnA.send("/app/chats/usr-empleado-001%3Ausr-psicologo-001/mensajes", Map.of("text", "Hola"));

        assertThat(lauraInbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "NUEVO");
        assertThat(davidOtherTab.poll(5, TimeUnit.SECONDS)).containsEntry("type", "NUEVO");
        assertThat(lauraInbox.poll(1, TimeUnit.SECONDS)).as("sin duplicados").isNull();
    }

    @Test
    void spotsAndSlotsReachSubscribersOfBothReplicasExactlyOnce() throws Exception {
        StompSession onA = clients.connect(portA, Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S."));
        StompSession onB = clients.connect(portB, Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."));
        BlockingQueue<Map<String, Object>> spotsA = subscribe(onA, "/topic/proyectos/proyecto-9/cupos");
        BlockingQueue<Map<String, Object>> spotsB = subscribe(onB, "/topic/proyectos/proyecto-9/cupos");
        BlockingQueue<Map<String, Object>> slotsA = subscribe(onA, "/topic/franjas");
        BlockingQueue<Map<String, Object>> slotsB = subscribe(onB, "/topic/franjas");

        publish("zeal.marketplace.projects.spots", Map.of("projectId", "proyecto-9", "capacity", 10,
                "occupied", 4, "available", 6));
        publish("zeal.wellbeing.slots", Map.of("type", "OCUPADA", "slot", Map.of("id", "franja-1")));

        assertThat(spotsA.poll(5, TimeUnit.SECONDS)).containsEntry("type", "CUPOS").containsEntry("available", 6);
        assertThat(spotsB.poll(5, TimeUnit.SECONDS)).containsEntry("type", "CUPOS").containsEntry("available", 6);
        assertThat(slotsA.poll(5, TimeUnit.SECONDS)).containsEntry("type", "OCUPADA");
        assertThat(slotsB.poll(5, TimeUnit.SECONDS)).containsEntry("type", "OCUPADA");
        assertThat(spotsA.poll(1, TimeUnit.SECONDS)).as("una sola réplica procesa el evento").isNull();
        assertThat(slotsB.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void notificationIsStoredOnceAndReachesTheRecipientOnItsReplica() throws Exception {
        String userId = "usr-empleado-003";
        redis.delete("zeal:notifications:" + userId);
        StompSession onB = clients.connect(portB, Tokens.token(userId, "EMPLEADO", "Nexura S.A.S."));
        BlockingQueue<Map<String, Object>> inbox = subscribe(onB, "/user/queue/notificaciones");

        publish("zeal.notifications", Map.of("userId", userId, "type", "RESERVA_CONFIRMADA",
                "title", "Tu cita quedó confirmada", "message", "Laura · lunes", "link", "/empleado/mis-citas"));

        assertThat(inbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "NUEVA");
        assertThat(inbox.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(redis.opsForHash().size("zeal:notifications:" + userId)).isEqualTo(1);
    }

    @Test
    void adminsOfTheOrganizationOnAnyReplicaReceiveTheirPanelUpdate() throws Exception {
        StompSession adminOnB = clients.connect(portB, Tokens.token("usr-admin-001", "ADMIN", "Nexura S.A.S."));
        StompSession otherAdminOnA = clients.connect(portA, Tokens.token("usr-admin-002", "ADMIN", "Órbita Labs S.A.S."));
        BlockingQueue<Map<String, Object>> panel = subscribe(adminOnB, "/user/queue/proyectos");
        BlockingQueue<Map<String, Object>> otherPanel = subscribe(otherAdminOnA, "/user/queue/proyectos");

        publish("zeal.marketplace.projects.published", Map.of("organization", "Nexura S.A.S.",
                "project", Map.of("id", "proyecto-2"), "detail", Map.of("id", "proyecto-2")));

        assertThat(panel.poll(5, TimeUnit.SECONDS)).containsEntry("type", "PUBLICADO");
        assertThat(panel.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(otherPanel.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void webRtcSignalCrossesReplicas() throws Exception {
        String davidToken = Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.");
        StompSession davidOnA = clients.connect(portA, davidToken);
        StompSession lauraOnB = clients.connect(portB, Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL"));
        BlockingQueue<Map<String, Object>> lauraInbox = subscribe(lauraOnB, "/user/queue/salas");
        when(WELLBEING.room(eq(davidToken), eq("reserva-7"))).thenReturn(Optional.of(new WellbeingClient.RoomAccess(
                "reserva-7", "ACTIVA", java.time.Instant.now().plusSeconds(1800), "usr-psicologo-001")));
        when(WELLBEING.room(any(), eq("otra"))).thenReturn(Optional.empty());

        davidOnA.send("/app/salas/reserva-7/senales", Map.of("type", "OFERTA", "id", "o-1", "sdp", "v=0"));

        assertThat(lauraInbox.poll(5, TimeUnit.SECONDS)).containsEntry("type", "SENAL");
    }

    /**
     * R2-13 / SEG-4: el Wellbeing Service revoca una sesión (lista negra + Pub/Sub) y las conexiones STOMP
     * de ese token se cierran en las DOS réplicas en menos de 1 s; el mismo token ya no puede conectarse.
     */
    @Test
    void revokingASessionClosesItsStompConnectionsOnEveryReplicaWithinOneSecond() throws Exception {
        String stolen = Tokens.token("usr-empleado-001", "EMPLEADO", "Nexura S.A.S.", "sesion-robada");
        StompSession onA = clients.connect(portA, stolen);
        StompSession onB = clients.connect(portB, stolen);
        StompSession bystander = clients.connect(portB, Tokens.token("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."));

        long revokedAt = System.nanoTime();
        redis.opsForValue().set("zeal:revoked:sid:sesion-robada", "REFRESH_REUTILIZADO", java.time.Duration.ofMinutes(15));
        redis.convertAndSend("zeal.security.revocations", "{\"sid\":\"sesion-robada\",\"reason\":\"REFRESH_REUTILIZADO\"}");

        while ((onA.isConnected() || onB.isConnected()) && System.nanoTime() - revokedAt < 3_000_000_000L) {
            Thread.sleep(20);
        }
        long millis = (System.nanoTime() - revokedAt) / 1_000_000;
        assertThat(onA.isConnected()).isFalse();
        assertThat(onB.isConnected()).isFalse();
        assertThat(millis).as("cierre en todas las réplicas").isLessThan(1000);
        assertThat(bystander.isConnected()).as("las demás sesiones siguen").isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> clients.connect(portA, stolen))
                .as("el token revocado ya no conecta").isInstanceOf(Exception.class);
    }

    /** R2-9: una suscripción a un tópico de otra empresa se rechaza y se envía a la auditoría. */
    @Test
    void foreignSubscriptionsAreRejectedAndAudited() throws Exception {
        java.util.concurrent.BlockingQueue<String> audit = new java.util.concurrent.LinkedBlockingQueue<>();
        var container = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(replicaA.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        container.addMessageListener((message, pattern) -> audit.add(new String(message.getBody())),
                new org.springframework.data.redis.listener.ChannelTopic("zeal.security.audit"));
        container.afterPropertiesSet();
        container.start();
        Thread.sleep(300);
        StompSession outsider = clients.connect(portA, Tokens.token("usr-otra", "EMPLEADO", "Órbita Labs S.A.S."));

        subscribe(outsider, "/topic/organizaciones/Nexura%20S.A.S./proyectos");

        String event = audit.poll(3, TimeUnit.SECONDS);
        container.stop();
        assertThat(event).contains("\"eventType\":\"STOMP_SUSCRIPCION\"").contains("\"outcome\":\"DENEGADO\"")
                .contains("\"userId\":\"usr-otra\"");
        assertThat(outsider.isConnected()).isFalse();
    }

    private static ConfigurableApplicationContext startReplica(String name) {
        Map<String, Object> properties = new HashMap<>(Infrastructure.properties());
        properties.put("server.port", 0);
        properties.put("spring.application.name", "zeal-realtime-gateway-" + name);
        properties.put("app.presence.ttl", "3s");
        properties.put("app.presence.sweep-interval", "1s");
        // Como argumentos de línea de comandos: tienen prioridad sobre application.yml.
        return new SpringApplicationBuilder(RealtimeGatewayApplication.class, StubWellbeing.class)
                .run(properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    private void publish(String channel, Object event) throws Exception {
        redis.convertAndSend(channel, objectMapper.writeValueAsString(event));
    }

    private static String presenceViaRest(int port, String userId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/presencia/" + userId))
                .header("Authorization", "Bearer " + Tokens.token("usr-psicologo-001", "PSICOLOGO", "ZEAL")).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
