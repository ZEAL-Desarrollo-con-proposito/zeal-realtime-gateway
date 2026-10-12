package co.zeal.realtime.websocket;

import co.zeal.realtime.shared.security.AuthenticatedUser;
import co.zeal.realtime.shared.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StompSubscriptionGuardTest {

    private static StompPrincipal user(Role role, String organization) {
        return new StompPrincipal(new AuthenticatedUser("id", role, "Persona", "p@zeal.co", organization), "token");
    }

    @Test
    void privateQueuesAndSpotCountersAreOpenToEveryone() {
        StompPrincipal admin = user(Role.ADMIN, "Nexura S.A.S.");
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(admin, "/user/queue/notificaciones")).isTrue();
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(admin, "/topic/proyectos/p-1/cupos")).isTrue();
    }

    @Test
    void wellbeingTopicsAreClosedToTheBusinessDomain() {
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(user(Role.ADMIN, "Nexura S.A.S."), "/topic/franjas"))
                .isFalse();
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(user(Role.EMPLEADO, "Nexura S.A.S."), "/topic/franjas"))
                .isTrue();
    }

    @Test
    void organizationTopicsRequireTheSameCompany() {
        StompPrincipal nexura = user(Role.EMPLEADO, "Nexura S.A.S.");
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(nexura,
                "/topic/organizaciones/Nexura%20S.A.S./talleres")).isTrue();
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(nexura,
                "/topic/organizaciones/%C3%93rbita%20Labs%20S.A.S./talleres")).isFalse();
    }

    @Test
    void unknownDestinationsAreDenied() {
        assertThat(StompSubscriptionGuard.isSubscriptionAllowed(user(Role.EMPLEADO, "Nexura S.A.S."), "/topic/sala.r-1"))
                .isFalse();
    }

    @Test
    void encodesLikeJavascriptEncodeUriComponent() {
        assertThat(Destinations.encode("Órbita Labs S.A.S.")).isEqualTo("%C3%93rbita%20Labs%20S.A.S.");
        assertThat(Destinations.encode("a:b/c")).isEqualTo("a%3Ab%2Fc");
    }
}
