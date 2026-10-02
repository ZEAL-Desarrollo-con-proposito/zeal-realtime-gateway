package api.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthSecurityTests.AuthorizationProbeController.class)
class AuthSecurityTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginReturnsJwtForAllSeededRoles() throws Exception {
        assertLogin("admin", "Admin123!", "ADMIN");
        assertLogin("psicologo1", "Psicologo123!", "PSICOLOGO");
        assertLogin("empleado1", "Empleado123!", "EMPLEADO");
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsRequireTheMatchingRole() throws Exception {
        String adminToken = tokenFor("admin", "Admin123!");
        String psicologoToken = tokenFor("psicologo1", "Psicologo123!");
        String empleadoToken = tokenFor("empleado1", "Empleado123!");

        mockMvc.perform(get("/api/admin/probe").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/probe").header("Authorization", bearer(psicologoToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/psicologo/probe").header("Authorization", bearer(psicologoToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empleado/probe").header("Authorization", bearer(empleadoToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empleado/probe"))
                .andExpect(status().isUnauthorized());
    }

    private void assertLogin(String username, String password, String role) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value(role));
    }

    private String tokenFor(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @RestController
    static class AuthorizationProbeController {
        @GetMapping({"/api/admin/probe", "/api/psicologo/probe", "/api/empleado/probe"})
        String probe() {
            return "ok";
        }
    }
}