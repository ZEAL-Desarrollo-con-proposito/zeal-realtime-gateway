package api.api.auth.model;

public class Usuario {
    private final Long id;
    private final String username;
    private final String password;
    private final Rol role;
    private final boolean activo;

    public Usuario(Long id, String username, String password, Rol role, boolean activo) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Rol getRole() {
        return role;
    }

    public boolean isActivo() {
        return activo;
    }
}