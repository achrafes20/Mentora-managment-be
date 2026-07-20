package ma.hbdev.rh.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import ma.hbdev.rh.shared.mattermost.MattermostClient;
import ma.hbdev.rh.shared.mattermost.ResultatMattermost;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class NotificationIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NotificationService notificationService;

  @MockitoBean private MattermostClient mattermostClient;

  private String adminToken;
  private String managerToken;
  private String autreManagerToken;
  private UUID managerId;

  @BeforeEach
  void preparerUtilisateurs() throws Exception {
    when(mattermostClient.envoyer(anyString()))
        .thenReturn(ResultatMattermost.echec("Mattermost indisponible pour le test"));

    String suffixe = UUID.randomUUID().toString();
    User admin = utilisateur("admin-" + suffixe + "@test.ma", RoleUtilisateur.admin);
    User manager = utilisateur("manager-" + suffixe + "@test.ma", RoleUtilisateur.manager);
    User autreManager =
        utilisateur("manager-autre-" + suffixe + "@test.ma", RoleUtilisateur.manager);
    managerId = manager.getId();

    adminToken = login(admin.getEmail(), "TestPass@2026");
    managerToken = login(manager.getEmail(), "TestPass@2026");
    autreManagerToken = login(autreManager.getEmail(), "TestPass@2026");
  }

  @Test
  void creationEmployeCreeInAppJournalMattermostEtAuditSansBloquer() throws Exception {
    UUID departementId = creerDepartement();
    String employeId = creerEmploye(departementId, "notifie@test.ma");

    String notificationId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/notifications").header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(
                        jsonPath("$.data.content[0].titre")
                            .value("Nouvel employe dans votre equipe"))
                    .andExpect(jsonPath("$.data.content[0].lu").value(false))
                    .andExpect(jsonPath("$.data.content[0].mattermostTente").value(true))
                    .andExpect(jsonPath("$.data.content[0].mattermostReussi").value(false))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .at("/data/content/0/id")
            .asText();

    Integer audit =
        jdbcTemplate.queryForObject(
            "select count(*) from journal_audit where entite_id = ?::uuid and action = 'creation'",
            Integer.class,
            employeId);
    Integer mattermost =
        jdbcTemplate.queryForObject(
            "select count(*) from notifications_mattermost where entite_id = ?::uuid and echec = true",
            Integer.class,
            employeId);
    org.assertj.core.api.Assertions.assertThat(audit).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(mattermost).isEqualTo(1);

    mockMvc
        .perform(
            patch("/api/notifications/{id}/lire", notificationId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lu").value(true));

    mockMvc
        .perform(
            get("/api/notifications/non-lues/count")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(0));
  }

  @Test
  void unUtilisateurNePeutPasLireLaNotificationDUnAutre() throws Exception {
    UUID departementId = creerDepartement();
    creerEmploye(departementId, "isole@test.ma");
    String notificationId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/notifications").header("Authorization", "Bearer " + managerToken))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .at("/data/content/0/id")
            .asText();

    mockMvc
        .perform(
            patch("/api/notifications/{id}/lire", notificationId)
                .header("Authorization", "Bearer " + autreManagerToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void lectureGlobaleEtArchivageRespectentLeDestinataireEtLaRetention() throws Exception {
    UUID departementId = creerDepartement();
    creerEmploye(departementId, "premier@test.ma");
    creerEmploye(departementId, "second@test.ma");

    mockMvc
        .perform(
            patch("/api/notifications/lire-toutes")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nombreMisAJour").value(2));

    jdbcTemplate.update(
        "update notifications_in_app set cree_le = ? where destinataire_id = ?",
        Timestamp.from(Instant.now().minus(91, ChronoUnit.DAYS)),
        managerId);
    notificationService.archiverExpirees();

    mockMvc
        .perform(get("/api/notifications").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  private User utilisateur(String email, RoleUtilisateur role) {
    User user = new User();
    user.setEmail(email);
    user.setMotDePasseHash(passwordEncoder.encode("TestPass@2026"));
    user.setRole(role);
    user.setNom("Test");
    user.setPrenom(role.name());
    return userRepository.save(user);
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
        .at("/data/token")
        .asText();
  }

  private UUID creerDepartement() throws Exception {
    String corps =
        """
        {"nom":"Equipe Notifications %s","managerId":"%s"}
        """
            .formatted(UUID.randomUUID(), managerId);
    String reponse =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corps))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(reponse).at("/data/id").asText());
  }

  private String creerEmploye(UUID departementId, String email) throws Exception {
    String corps =
        """
        {"nom":"Dupont","prenom":"Jean","email":"%s","poste":"Developpeur",
         "departementId":"%s","managerId":"%s","dateEmbauche":"2026-01-15",
         "typeContrat":"CDI"}
        """
            .formatted(email, departementId, managerId);
    String reponse =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corps))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(reponse).at("/data/id").asText();
  }
}
