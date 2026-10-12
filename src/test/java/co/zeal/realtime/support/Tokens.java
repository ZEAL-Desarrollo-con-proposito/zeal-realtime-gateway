package co.zeal.realtime.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Emite tokens como los del Wellbeing Service, firmados con el secreto de desarrollo. */
public final class Tokens {

    public static final String SECRET = "ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!";

    private Tokens() {
    }

    public static String token(String userId, String role, String organization) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("zeal").subject(userId).issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1))).claim("role", role).claim("fullName", "Persona " + userId)
                .claim("email", userId + "@zeal.co").claim("organization", organization).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
