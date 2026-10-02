package api.api.auth.repository;

import api.api.auth.model.Rol;
import api.api.auth.model.Usuario;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUsuarioRepository {
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

    public InMemoryUsuarioRepository(
            PasswordEncoder passwordEncoder,
            @Value("${zeal.auth.seed.admin-password}") String adminPassword,
            @Value("${zeal.auth.seed.psicologo1-password}") String psicologo1Password,
            @Value("${zeal.auth.seed.psicologo2-password}") String psicologo2Password,
            @Value("${zeal.auth.seed.empleado1-password}") String empleado1Password,
            @Value("${zeal.auth.seed.empleado2-password}") String empleado2Password,
            @Value("${zeal.auth.seed.empleado3-password}") String empleado3Password) {
        add(passwordEncoder, 1L, "admin", adminPassword, Rol.ADMIN);
        add(passwordEncoder, 2L, "psicologo1", psicologo1Password, Rol.PSICOLOGO);
        add(passwordEncoder, 3L, "psicologo2", psicologo2Password, Rol.PSICOLOGO);
        add(passwordEncoder, 4L, "empleado1", empleado1Password, Rol.EMPLEADO);
        add(passwordEncoder, 5L, "empleado2", empleado2Password, Rol.EMPLEADO);
        add(passwordEncoder, 6L, "empleado3", empleado3Password, Rol.EMPLEADO);
    }

    public Optional<Usuario> findByUsername(String username) {
        return Optional.ofNullable(usuarios.get(username));
    }

    public Collection<Usuario> findAll() {
        return usuarios.values();
    }

    private void add(PasswordEncoder encoder, Long id, String username, String rawPassword, Rol role) {
        usuarios.put(username, new Usuario(id, username, encoder.encode(rawPassword), role, true));
    }
}