package ma.hbdev.rh.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.SessionRepository;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
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

/** Réplique le pattern de {@code MigrationIntegrationTest} : Testcontainers, PostgreSQL 14 réel. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DepartementIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String managerToken;

  @BeforeEach
  void authentifierAdminEtManager() throws Exception {
    nettoyerTracesTransverses();
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    User admin = new User();
    admin.setEmail("admin@hbdev.ma");
    admin.setMotDePasseHash(passwordEncoder.encode("AdminPass@2025"));
    admin.setRole(RoleUtilisateur.admin);
    admin.setNom("System");
    admin.setPrenom("Admin");
    userRepository.save(admin);

    User manager = new User();
    manager.setEmail("manager@hbdev.ma");
    manager.setMotDePasseHash(passwordEncoder.encode("ManagerPass@2025"));
    manager.setRole(RoleUtilisateur.manager);
    manager.setNom("Dupont");
    manager.setPrenom("Jean");
    userRepository.save(manager);

    adminToken = login("admin@hbdev.ma", "AdminPass@2025");
    managerToken = login("manager@hbdev.ma", "ManagerPass@2025");
  }

  private void nettoyerTracesTransverses() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE notifications_mattermost, notifications_in_app, journal_audit");
  }

  private String login(String email, String motDePasse) throws Exception {
    String requete = objectMapper.writeValueAsString(new LoginRequest(email, motDePasse));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requete))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .at("/data/token")
        .asText();
  }

  @Test
  void creeListeModifieEtDesactiveUnDepartement() throws Exception {
    String requeteCreation =
        objectMapper.writeValueAsString(new DepartementRequete("Ressources Humaines", null));

    String reponseCreation =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nom").value("Ressources Humaines"))
            .andExpect(jsonPath("$.data.statut").value("actif"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(get("/api/departements").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.nom == 'Ressources Humaines')]").exists());

    String requeteModification =
        objectMapper.writeValueAsString(new DepartementRequete("RH & Paie", null));
    mockMvc
        .perform(
            put("/api/departements/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteModification))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nom").value("RH & Paie"));

    mockMvc
        .perform(
            delete("/api/departements/{id}", id).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/departements").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.nom == 'RH & Paie')].statut").value("inactif"));

    mockMvc
        .perform(
            post("/api/departements/{id}/activer", id)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("actif"));
  }

  @Test
  void refuseUnNomDeDepartementDejaUtilise() throws Exception {
    String requete = objectMapper.writeValueAsString(new DepartementRequete("Finance", null));

    mockMvc
        .perform(
            post("/api/departements")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/departements")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void renvoie404SurUnDepartementInconnu() throws Exception {
    String requete = objectMapper.writeValueAsString(new DepartementRequete("Peu importe", null));

    mockMvc
        .perform(
            put("/api/departements/{id}", "00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isNotFound());
  }

  // ApiAuthenticationEntryPoint (SecurityConfig) répond 401 pour un principal anonyme — même
  // convention que GlobalExceptionHandler#handleAuthentication pour un jeton présent mais
  // invalide/expiré, désormais cohérente que le refus vienne de la chaîne de filtres ou du
  // dispatch Spring MVC. Régression : avant ApiAuthenticationEntryPoint, ce cas retombait sur le
  // comportement par défaut de Spring Security — 403 à corps vide, hors du format ApiResponse — le
  // corps est donc vérifié ici, pas seulement le statut.
  @Test
  void requeteAnonymeEstRefusee() throws Exception {
    mockMvc
        .perform(get("/api/departements"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("Authentification requise"));
  }

  @Test
  void managerPeutListerMaisPasCreerUnDepartement() throws Exception {
    mockMvc
        .perform(get("/api/departements").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    String requete = objectMapper.writeValueAsString(new DepartementRequete("Nouveau", null));
    mockMvc
        .perform(
            post("/api/departements")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isForbidden());
  }
}
