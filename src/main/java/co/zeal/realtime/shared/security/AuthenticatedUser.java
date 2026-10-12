package co.zeal.realtime.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Identidad de quien hace la petición, tomada del JWT ya validado. */
public record AuthenticatedUser(String id, Role role, String fullName, String email, String organization) {

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(
                jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsString(ZealClaims.ROLE)),
                jwt.getClaimAsString(ZealClaims.FULL_NAME),
                jwt.getClaimAsString(ZealClaims.EMAIL),
                jwt.getClaimAsString(ZealClaims.ORGANIZATION));
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }
}
