package co.zeal.realtime.chat;

import java.security.Principal;

public final class RealtimePrincipal implements Principal {

    private final String userId;
    private final String role;
    private final String accessToken;

    public RealtimePrincipal(String userId, String role, String accessToken) {
        this.userId = userId;
        this.role = role;
        this.accessToken = accessToken;
    }

    @Override
    public String getName() {
        return userId;
    }

    public String userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public String accessToken() {
        return accessToken;
    }
}
