package co.zeal.realtime.broker;

import co.zeal.realtime.websocket.Destinations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerDestinationsTest {

    @Test
    void organizationTopicBecomesADottedRoutingKeyWithEscapedDots() {
        String contract = Destinations.organizationProjectsTopic("Nexura S.A.S.");

        assertThat(BrokerDestinations.toBroker(contract))
                .isEqualTo("/topic/organizaciones.Nexura%20S%2EA%2ES%2E.proyectos");
    }

    @Test
    void wildcardsInASegmentNeverReachTheBrokerAsWildcards() {
        assertThat(BrokerDestinations.toBroker("/topic/presencia/*")).isEqualTo("/topic/presencia.%2A");
        assertThat(BrokerDestinations.toBroker("/topic/presencia/#")).isEqualTo("/topic/presencia.%23");
    }

    @Test
    void queuesAndOtherDestinationsAreLeftUntouched() {
        assertThat(BrokerDestinations.toBroker("/queue/notificaciones-userabc")).isEqualTo("/queue/notificaciones-userabc");
        assertThat(BrokerDestinations.toBroker("/topic/franjas")).isEqualTo("/topic/franjas");
        assertThat(BrokerDestinations.toBroker(null)).isNull();
        assertThat(BrokerDestinations.fromBroker("/user/queue/chats")).isEqualTo("/user/queue/chats");
    }

    /** RabbitMQ devuelve el destino de cada MESSAGE con {@code %} escapado como {@code %25}. */
    @ParameterizedTest
    @ValueSource(strings = {"/topic/organizaciones/Nexura%20S.A.S./proyectos", "/topic/proyectos/p-1/cupos",
            "/topic/presencia/usr-empleado-001", "/topic/franjas", "/topic/organizaciones/%C3%93rbita%20Labs/talleres"})
    void roundTripsThroughTheBrokerEscaping(String contract) {
        String asDelivered = BrokerDestinations.toBroker(contract).replace("%", "%25");

        assertThat(BrokerDestinations.fromBroker(asDelivered)).isEqualTo(contract);
    }
}
