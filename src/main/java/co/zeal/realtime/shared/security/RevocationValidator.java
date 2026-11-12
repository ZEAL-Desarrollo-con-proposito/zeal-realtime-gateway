package co.zeal.realtime.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;

/**
 * R2-13 · Rechaza los access tokens revocados consultando la lista negra que mantiene el Wellbeing
 * Service en Redis: por sesión ({@code sid}, familia de refresh tokens) o por token ({@code jti}).
 * Si Redis no responde se deja pasar (el token vence en 15 min) y se registra el error.
 */
public class RevocationValidator implements OAuth2TokenValidator<Jwt> {

    public static final String SESSION_PREFIX = "zeal:revoked:sid:";
    public static final String TOKEN_PREFIX = "zeal:revoked:jti:";
    private static final Logger log = LoggerFactory.getLogger(RevocationValidator.class);
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "La sesión fue revocada", null);

    private final StringRedisTemplate redis;

    public RevocationValidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> keys = new ArrayList<>(2);
        if (token.getId() != null) {
            keys.add(TOKEN_PREFIX + token.getId());
        }
        if (token.getClaimAsString(ZealClaims.SESSION) != null) {
            keys.add(SESSION_PREFIX + token.getClaimAsString(ZealClaims.SESSION));
        }
        if (keys.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }
        try {
            Long revoked = redis.countExistingKeys(keys);
            return revoked != null && revoked > 0 ? OAuth2TokenValidatorResult.failure(REVOKED)
                    : OAuth2TokenValidatorResult.success();
        } catch (DataAccessException e) {
            // TODO(ZEAL-REVIEW): fail-open ante caída de Redis (disponibilidad) vs fail-closed (seguridad).
            log.error("No se pudo consultar la lista negra de tokens: {}", e.getMessage());
            return OAuth2TokenValidatorResult.success();
        }
    }
}
