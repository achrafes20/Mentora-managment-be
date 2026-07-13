package ma.hbdev.rh.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
class UserIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;

  private String adminToken;
  private String managerToken;
  private User managerUser;

  @BeforeEach
  void setUp() throws Exception {
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    // Create Admin
    User admin = new User();
    admin.setEmail("admin@hbdev.ma");
    admin.setMotDePasseHash(passwordEncoder.encode("AdminPass@2025"));
    admin.setRole(RoleUtilisateur.admin);
    admin.setNom("System");
    admin.setPrenom("Admin");
    admin.setStatut(StatutActifInactif.actif);
    admin = userRepository.save(admin);

    // Create Manager
    managerUser = new User();
    managerUser.setEmail("manager@hbdev.ma");
    managerUser.setMotDePasseHash(passwordEncoder.encode("ManagerPass@2025"));
    managerUser.setRole(RoleUtilisateur.manager);
    managerUser.setNom("Dupont");
    managerUser.setPrenom("Jean");
    managerUser.setStatut(StatutActifInactif.actif);
    managerUser = userRepository.save(managerUser);

    // Logins to get tokens
    adminToken = performLogin("admin@hbdev.ma", "AdminPass@2025");
    managerToken = performLogin("manager@hbdev.ma", "ManagerPass@2025");
  }

  private String performLogin(String email, String mdp) throws Exception {
    LoginRequest req = new LoginRequest(email, mdp);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andReturn();

    String content = result.getResponse().getContentAsString();
    ApiResponseEnvelope response = objectMapper.readValue(content, ApiResponseEnvelope.class);
    return response.data.token;
  }

  @Test
  void adminCanListUsers() throws Exception {
    mockMvc
        .perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void managerCannotListUsers() throws Exception {
    mockMvc
        .perform(get("/api/users").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateUser() throws Exception {
    UserCreateRequest req =
        new UserCreateRequest(
            "new.user@hbdev.ma", "SecurePass@123", RoleUtilisateur.manager, "New", "User");

    mockMvc
        .perform(
            post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("new.user@hbdev.ma"));
  }

  @Test
  void adminCanDeactivateUser() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/" + managerUser.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("inactif"));

    User updated = userRepository.findById(managerUser.getId()).orElseThrow();
    assertThat(updated.getStatut()).isEqualTo(StatutActifInactif.inactif);
  }

  private static class ApiResponseEnvelope {
    public boolean success;
    public LoginResponseEnvelope data;
  }

  private static class LoginResponseEnvelope {
    public String token;
  }
}
