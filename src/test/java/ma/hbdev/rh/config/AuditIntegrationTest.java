package ma.hbdev.rh.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.SessionRepository;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

  // Régression bout en bout (contrairement aux tests ci-dessus, qui vérifient le mécanisme de
  // recherche sur des lignes insérées à la main avec details déjà rempli) : AuthService/UserService
  // ne publiaient jusqu'ici aucun EvenementMetier, donc aucune action de compte (création,
  // verrouillage, réinitialisation de mot de passe...) n'apparaissait dans journal_audit — un vrai
  // trou NFR-SEC-03. Ce test passe par une vraie création de compte via l'API, pas une insertion
  // directe, pour vérifier que l'événement est réellement publié ET que details() est peuplé (sinon
  // la recherche par nom/e-mail, EF-CFG-04, ne trouve jamais rien).
  @Test
  void creationDeCompteEstAuditeeEtTrouvableParRechercheTexteLibre() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"nouveau.compte@hbdev.ma","motDePasse":"NouveauPass@2025",
                     "role":"manager","nom":"Benali","prenom":"Yassir"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/audit")
                .param("module", "authentification")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].action").value("creation"))
        .andExpect(jsonPath("$.data.content[0].details.email").value("nouveau.compte@hbdev.ma"));

    mockMvc
        .perform(
            get("/api/audit")
                .param("recherche", "nouveau.compte@hbdev.ma")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  // Régression bout en bout, même motif que le test compte ci-dessus : DemandeAdministrativeEvent
  // portait déjà employeNomComplet (utilisé pour le corps de la notification) mais n'exposait
  // jamais ce champ via details() — recherche libre par nom d'employé toujours vide sur la
  // catégorie la plus fréquente du journal (creation/approbation/rejet/annulation).
  @Test
  void demandeAdministrativeEstAuditeeEtTrouvableParRechercheTexteLibre() throws Exception {
    UUID deptId =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            post("/api/departements")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nom\":\"Audit Test\",\"managerId\":null}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .at("/data/id")
                .asText());

    UUID employeId =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            post("/api/employes")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"nom":"Zniber","prenom":"Salwa","email":"salwa.zniber@hbdev.ma",
                                     "poste":"Dev","departementId":"%s","dateEmbauche":"2025-01-01",
                                     "typeContrat":"CDI"}
                                    """
                                        .formatted(deptId)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .at("/data/id")
                .asText());

    mockMvc
        .perform(
            post("/api/demandes-administratives")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"employeId":"%s","typeDemande":"bon_sortie","dateDebut":"%s",
                     "heureDepart":"10:00","heureRetourPrevue":"11:00","motif":"RDV"}
                    """
                        .formatted(employeId, LocalDate.now().plusDays(1))))
        .andExpect(status().isOk());

    // 2, pas 1 : "Zniber" matche aussi l'entrée employe.creation de Salwa elle-même (même mécanisme
    // EF-CFG-04, cf. EmployeModifieEvent#details()) — le point testé ici est que l'entrée
    // demande_administrative apparaisse désormais dans les résultats, pas qu'elle soit seule.
    mockMvc
        .perform(
            get("/api/audit")
                .param("recherche", "Zniber")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath("$.data.content[?(@.module == 'demande_administrative')].details.employe")
                .value("Salwa Zniber"));
  }

  @Test
  void refuseAuManager() throws Exception {
    mockMvc
        .perform(get("/api/audit").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  // EF-CFG-05
  @Test
  void exporteLeJournalDAuditFiltreParModuleEnExcel() throws Exception {
    // "creation"/"employe" : convention réellement utilisée par les EvenementMetier (cf.
    // EmployeModifieEvent), pas la forme synthétique "employe.creation" du premier jet de ce test —
    // corrigée en même temps que la fusion Action/Entité (AuditExportService#libelleAction).
    insererEntree(adminId, "creation", "employe", "employe", Instant.now());
    insererEntree(
        adminId, "config.modif", "configuration", "configuration_parametre", Instant.now());

    byte[] corps =
        mockMvc
            .perform(
                get("/api/audit/export")
                    .header("Authorization", "Bearer " + adminToken)
                    .param("format", "xlsx")
                    .param("module", "employe"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(corps))) {
      var feuille = classeur.getSheetAt(0);
      assertThat(feuille.getLastRowNum()).isEqualTo(1);
      // Action + Entité fusionnées en une seule cellule (EF-CFG-05, lisibilité) : "Création" (verbe
      // mappé) + "Employé" (entiteType mappé).
      assertThat(feuille.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Création - Employé");
    }
  }

  @Test
  void refuseAuManagerDExporterLAudit() throws Exception {
    mockMvc
        .perform(
            get("/api/audit/export")
                .header("Authorization", "Bearer " + managerToken)
                .param("format", "pdf"))
        .andExpect(status().isForbidden());
  }
}
