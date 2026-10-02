package api.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import api.api.auth.dto.LoginResponse;
import api.api.auth.model.Rol;
import api.api.auth.model.Usuario;
import api.api.auth.repository.InMemoryUsuarioRepository;
import api.api.auth.service.AuthService;
import api.api.auth.service.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock
    private InMemoryUsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @ParameterizedTest
    @CsvSource({"admin, ADMIN", "psicologo1, PSICOLOGO", "empleado1, EMPLEADO"})
    void loginReturnsTokenAndRoleForEachRole(String username, Rol role) {
        Usuario usuario = new Usuario(1L, username, "encoded-password", role, true);
        when(usuarioRepository.findByUsername(username)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correcta", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(username, role)).thenReturn("signed-token");

        LoginResponse response = authService.login(username, "correcta");

        assertEquals("signed-token", response.token());
        assertEquals(role.name(), response.role());
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login("admin", "incorrecta"));
    }
}