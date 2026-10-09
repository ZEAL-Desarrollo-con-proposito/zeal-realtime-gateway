package co.zeal.realtime.shared.security;

/** Nombres de los claims del JWT de ZEAL; los comparten los tres microservicios. */
public final class ZealClaims {

    public static final String ROLE = "role";
    public static final String FULL_NAME = "fullName";
    public static final String EMAIL = "email";
    public static final String ORGANIZATION = "organization";

    private ZealClaims() {
    }
}
