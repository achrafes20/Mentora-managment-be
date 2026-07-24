package ma.hbdev.rh.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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

/**
 * T4.B2 — EF-CFG-04/06 : consultation du journal d'audit (filtres, recherche pg_trgm) et garantie
 * de lecture seule (RULES SQL posées depuis V1__schema_initial.sql).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AuditIntegrationTest {

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

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

  private String adminToken;
  private String managerToken;
  private UUID adminId;
  private UUID managerId;

  @BeforeEach
  void nettoyerEtAuthentifier() throws Exception {
    jdbcTemplate.update("UPDATE configuration_parametres SET modifie_par = NULL");
    jdbcTemplate.execute(
        "TRUNCATE TABLE notifications_mattermost, notifications_in_app, journal_audit");
    sessionRepository.deleteAll();
    userRepository.deleteAll();

    User admin = new User();
    admin.setEmail("admin@hbdev.ma");
    admin.setMotDePasseHash(passwordEncoder.encode("AdminPass@2025"));
    admin.setRole(RoleUtilisateur.admin);
    admin.setNom("System");
    admin.setPrenom("Admin");
    admin = userRepository.save(admin);
    adminId = admin.getId();

    User manager = new User();
    manager.setEmail("manager@hbdev.ma");
    manager.setMotDePasseHash(passwordEncoder.encode("ManagerPass@2025"));
    manager.setRole(RoleUtilisateur.manager);
    manager.setNom("Dupont");
    manager.setPrenom("Jean");
    manager = userRepository.save(manager);
    managerId = manager.getId();

    adminToken = login("admin@hbdev.ma", "AdminPass@2025");
    managerToken = login("manager@hbdev.ma", "ManagerPass@2025");
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

  private UUID insererEntree(
      UUID utilisateurId, String action, String module, String entiteType, Instant horodatage) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into journal_audit(id, utilisateur_id, action, module, entite_type, details,
          en_delegation, horodatage)
        values (?, ?, ?, cast(? as module_audit), ?, cast(? as jsonb), false, ?)
        """,
        id,
        utilisateurId,
        action,
        module,
        entiteType,
        "{\"nom\": \"Dupont\"}",
        java.sql.Timestamp.from(horodatage));
    return id;
  }

  @Test
  void uneMiseAJourOuUneSuppressionDirecteEstSilencieusementIgnoree() {
    UUID id = insererEntree(adminId, "test.action", "configuration", "test", Instant.now());

    int lignesModifiees =
        jdbcTemplate.update("update journal_audit set action = 'hacked' where id = ?", id);
    assertThat(lignesModifiees).isZero();
    String actionEnBase =
        jdbcTemplate.queryForObject(
            "select action from journal_audit where id = ?", String.class, id);
    assertThat(actionEnBase).isEqualTo("test.action");

    int lignesSupprimees = jdbcTemplate.update("delete from journal_audit where id = ?", id);
    assertThat(lignesSupprimees).isZero();
    Integer nombreRestant =
        jdbcTemplate.queryForObject(
            "select count(*) from journal_audit where id = ?", Integer.class, id);
    assertThat(nombreRestant).isEqualTo(1);
  }

  @Test
  void filtreParModule() throws Exception {
    insererEntree(adminId, "employe.creation", "employe", "employe", Instant.now());
    insererEntree(
        adminId, "config.modif", "configuration", "configuration_parametre", Instant.now());

    mockMvc
        .perform(
            get("/api/audit")
                .param("module", "employe")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].module").value("employe"));
  }

  @Test
  void filtreParUtilisateur() throws Exception {
    insererEntree(adminId, "employe.creation", "employe", "employe", Instant.now());
    insererEntree(managerId, "employe.modification", "employe", "employe", Instant.now());

    mockMvc
        .perform(
            get("/api/audit")
                .param("utilisateurId", adminId.toString())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].utilisateurId").value(adminId.toString()));
  }

  @Test
  void filtreParPeriodeEnFuseauCasablanca() throws Exception {
    Instant hier = LocalDate.now(ZONE).minusDays(1).atTime(10, 0).atZone(ZONE).toInstant();
    Instant aujourdHui = LocalDate.now(ZONE).atTime(10, 0).atZone(ZONE).toInstant();
    insererEntree(adminId, "action.hier", "employe", "employe", hier);
    insererEntree(adminId, "action.aujourdhui", "employe", "employe", aujourdHui);

    mockMvc
        .perform(
            get("/api/audit")
                .param("debut", LocalDate.now(ZONE).toString())
                .param("fin", LocalDate.now(ZONE).toString())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].action").value("action.aujourdhui"));
  }

  @Test
  void rechercheTexteLibreSurActionEntiteEtDetails() throws Exception {
    insererEntree(adminId, "employe.creation", "employe", "employe", Instant.now());
    insererEntree(adminId, "recrutement.rejet", "recrutement", "candidature", Instant.now());

    mockMvc
        .perform(
            get("/api/audit")
                .param("recherche", "Dupont")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));

    mockMvc
        .perform(
            get("/api/audit")
                .param("recherche", "recrutement")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].action").value("recrutement.rejet"));
  }

  @Test
  void refuseAuManager() throws Exception {
    mockMvc
        .perform(get("/api/audit").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }
}
