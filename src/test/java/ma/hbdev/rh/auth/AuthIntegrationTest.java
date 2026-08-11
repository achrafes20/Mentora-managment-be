package ma.hbdev.rh.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthIntegrationTest {

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

  private User testUser;

  @BeforeEach
  void setUp() {
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    testUser = new User();
    testUser.setEmail("test@hbdev.ma");
    testUser.setMotDePasseHash(passwordEncoder.encode("Password@2025"));
    testUser.setRole(RoleUtilisateur.admin);
    testUser.setNom("Tester");
    testUser.setPrenom("Java");
    testUser.setStatut(StatutActifInactif.actif);
    testUser.setTentativesEchoueesConsecutives(0);
    testUser = userRepository.save(testUser);
  }

  @Test
  void loginSuccessReturnsToken() throws Exception {
    LoginRequest request = new LoginRequest("test@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(jsonPath("$.data.user.email").value("test@hbdev.ma"));
  }

  @Test
  void loginWrongPasswordIncrementsAttemptsAndLocks() throws Exception {
    LoginRequest badRequest = new LoginRequest("test@hbdev.ma", "WrongPass");

    // 4 bad attempts
    for (int i = 0; i < 4; i++) {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(badRequest)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error").value("Identifiants invalides"));
    }

    User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updatedUser.getTentativesEchoueesConsecutives()).isEqualTo(4);
    assertThat(updatedUser.isLocked()).isFalse();

    // 5th attempt: Lock the account
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("Identifiants invalides"));

    updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updatedUser.getTentativesEchoueesConsecutives()).isEqualTo(5);
    assertThat(updatedUser.isLocked()).isTrue();

    // Subsequent login attempts block immediately
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("test@hbdev.ma", "Password@2025"))))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.error")
                .value("Compte temporairement verrouillé. Réessayez dans quelques minutes."));
  }

  @Test
  void getMeRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void getMeWithValidTokenReturnsUser() throws Exception {
    LoginRequest request = new LoginRequest("test@hbdev.ma", "Password@2025");
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    String content = result.getResponse().getContentAsString();
    ApiResponseEnvelope response = objectMapper.readValue(content, ApiResponseEnvelope.class);
    String token = response.data.token;

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("test@hbdev.ma"));
  }

  // Envelope helpers for parsing MvcResult
  private static class ApiResponseEnvelope {
    public boolean success;
    public LoginResponseEnvelope data;
  }

  private static class LoginResponseEnvelope {
    public String token;
  }
}
