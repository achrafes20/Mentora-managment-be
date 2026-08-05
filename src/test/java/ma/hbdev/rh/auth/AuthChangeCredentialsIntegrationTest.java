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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Modification en libre-service de son propre e-mail et mot de passe, via {@code /api/auth/me}. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
class AuthChangeCredentialsIntegrationTest {

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
  @Autowired private JdbcTemplate jdbcTemplate;

  private User testUser;
  private User autreUser;

  @BeforeEach
  void setUp() {
    // journal_audit référence utilisateurs (NFR-SEC-03, comptes désormais audités) : à vider
    // avant, sinon userRepository.deleteAll() ci-dessous viole la FK dès le 2e test.
    jdbcTemplate.execute("TRUNCATE TABLE journal_audit");
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    testUser = new User();
    testUser.setEmail("admin@hbdev.ma");
    testUser.setMotDePasseHash(passwordEncoder.encode("Password@2025"));
    testUser.setRole(RoleUtilisateur.admin);
    testUser.setNom("Tester");
    testUser.setPrenom("Java");
    testUser.setStatut(StatutActifInactif.actif);
    testUser.setTentativesEchoueesConsecutives(0);
    testUser = userRepository.save(testUser);

    autreUser = new User();
    autreUser.setEmail("autre@hbdev.ma");
    autreUser.setMotDePasseHash(passwordEncoder.encode("Password@2025"));
    autreUser.setRole(RoleUtilisateur.manager);
    autreUser.setNom("Autre");
    autreUser.setPrenom("Compte");
    autreUser.setStatut(StatutActifInactif.actif);
    autreUser = userRepository.save(autreUser);
  }

  private String login(String email, String motDePasse) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, motDePasse))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("token")
        .asText();
  }

  // ---- Changement d'e-mail ----

  @Test
  void changerEmailRequiresAuth() throws Exception {
    mockMvc
        .perform(
            patch("/api/auth/me/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ChangeEmailRequest("nouveau@hbdev.ma"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changerEmailSuccessUpdatesAddressAndRevokesSession() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/email")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangeEmailRequest("nouveau-admin@hbdev.ma"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    User updated = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updated.getEmail()).isEqualTo("nouveau-admin@hbdev.ma");

    // Le jeton émis avant le changement (sujet = ancien e-mail) ne doit plus être utilisable :
    // sa session a été révoquée, y compris celle qui a fait la demande elle-même.
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    // Une reconnexion avec le nouvel e-mail fonctionne.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("nouveau-admin@hbdev.ma", "Password@2025"))))
        .andExpect(status().isOk());
  }

  @Test
  void changerEmailAlreadyUsedByAnotherAccountRejected() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/email")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeEmailRequest("autre@hbdev.ma"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Cet e-mail est déjà utilisé par un autre compte."));
  }

  @Test
  void changerEmailToOwnCurrentAddressRejected() throws Exception {
    // Le bug réel repéré par Taha : soumettre sa propre adresse comme "nouvel e-mail" était
    // accepté en silence (aucun changement réel), révoquait quand même la session, et donnait
    // l'impression que la fonctionnalité était cassée puisque l'ancienne adresse "fonctionnait
    // encore" — elle n'avait simplement jamais changé. Doit être rejeté explicitement.
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/email")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeEmailRequest("admin@hbdev.ma"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Le nouvel e-mail doit être différent de l'actuel."));

    // Rien n'a changé : la session reste utilisable (contrairement à un vrai changement).
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void changerEmailToOwnCurrentAddressRejectedCaseInsensitive() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/email")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeEmailRequest("ADMIN@hbdev.ma"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Le nouvel e-mail doit être différent de l'actuel."));
  }

  // ---- Changement de mot de passe ----

  @Test
  void changerMotDePasseRequiresAuth() throws Exception {
    mockMvc
        .perform(
            patch("/api/auth/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest("Password@2025", "Nouveau@2025"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changerMotDePasseSuccessAllowsLoginWithNewPassword() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest("Password@2025", "Nouveau@2025"))))
        .andExpect(status().isOk());

    // Session courante révoquée, comme pour le changement d'e-mail.
    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    // L'ancien mot de passe ne fonctionne plus.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@hbdev.ma", "Password@2025"))))
        .andExpect(status().isUnauthorized());

    // Le nouveau mot de passe fonctionne.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("admin@hbdev.ma", "Nouveau@2025"))))
        .andExpect(status().isOk());
  }

  @Test
  void changerMotDePasseWrongCurrentPasswordRejected() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest("MauvaisMotDePasse", "Nouveau@2025"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Mot de passe actuel incorrect."));
  }

  @Test
  void changerMotDePasseViolatingPolicyRejected() throws Exception {
    String token = login("admin@hbdev.ma", "Password@2025");

    mockMvc
        .perform(
            patch("/api/auth/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordRequest("Password@2025", "trop court"))))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.error")
                .value(
                    "Le nouveau mot de passe ne respecte pas la politique de sécurité "
                        + "(min. 10 caractères, majuscule, minuscule, chiffre)."));
  }
}
