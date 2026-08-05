package ma.hbdev.rh.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
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
 * T5.A1 — Tests d'intégration du tableau de bord (EF-DASH-01/02/RBAC). Vérifie : valeurs des
 * agrégats, RBAC (Admin vs Manager vs non-authentifié), périmètre Manager restreint au département.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DashboardIntegrationTest {

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

  private String adminToken;
  private String managerToken;
  private UUID managerId;
  private UUID departementId;
  private UUID employeId;

  @BeforeEach
  void preparerDonnees() throws Exception {
    // Nettoyer dans l'ordre (FK)
    jdbcTemplate.execute(
        """
        truncate table notifications_mattermost, notifications_in_app, journal_audit,
          anomalies_pointage, mouvements_conges, demandes_administratives, candidatures,
          jours_feries, periodes_blocage_conges, employes, departements, sessions_utilisateur
        cascade
        """);
    jdbcTemplate.update("UPDATE politique_conges SET modifie_par = NULL");
    jdbcTemplate.execute("DELETE FROM utilisateurs");

    String suffixe = UUID.randomUUID().toString();
    User admin = utilisateur("admin-" + suffixe + "@test.ma", RoleUtilisateur.admin);
    User manager = utilisateur("manager-" + suffixe + "@test.ma", RoleUtilisateur.manager);
    managerId = manager.getId();

    departementId = UUID.randomUUID();
    employeId = UUID.randomUUID();

    jdbcTemplate.update(
        "insert into departements(id, nom, manager_id, statut) values (?::uuid, ?, ?::uuid, 'actif')",
        departementId,
        "Ingénierie",
        managerId);

    // 2 employés actifs dans ce département
    insertEmploye(employeId, "actif", departementId);
    insertEmploye(UUID.randomUUID(), "actif", departementId);

    // 1 employé inactif (ne doit PAS être compté)
    insertEmploye(UUID.randomUUID(), "inactif", departementId);

    // 1 demande en attente pour cet employé
    jdbcTemplate.update(
        """
        insert into demandes_administratives(id, employe_id, type_demande, statut,
          granularite, date_debut, date_fin, motif)
        values (?::uuid, ?::uuid, 'conge', 'en_attente', 'journee', ?, ?, 'Test')
        """,
        UUID.randomUUID(),
        employeId,
        LocalDate.now().plusDays(10),
        LocalDate.now().plusDays(11));

    // 1 anomalie non résolue du jour
    jdbcTemplate.update(
        """
        insert into anomalies_pointage(id, employe_id, date_pointage, type_anomalie, resolue)
        values (?::uuid, ?::uuid, ?, 'absence_checkout', false)
        """,
        UUID.randomUUID(),
        employeId,
        LocalDate.now());

    adminToken = login(admin.getEmail());
    managerToken = login(manager.getEmail());
  }

  // ─── Tests Admin (EF-DASH-01) ──────────────────────────────────────────────

  @Test
  void adminVoitStatsGlobales() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        // 2 employés actifs (le 3e est inactif)
        .andExpect(jsonPath("$.data.employesActifs").value(2))
        // 1 demande en attente
        .andExpect(jsonPath("$.data.demandesEnAttente").value(1))
        // 1 anomalie du jour non résolue
        .andExpect(jsonPath("$.data.anomaliesDuJour").value(1))
        // La répartition par département doit être présente (Admin)
        .andExpect(jsonPath("$.data.repartitionParDepartement").isArray())
        .andExpect(jsonPath("$.data.repartitionParDepartement[0].nom").value("Ingénierie"))
        .andExpect(jsonPath("$.data.repartitionParDepartement[0].count").value(2))
        // Candidatures et fins de contrat présents (null → 0 en base vide)
        .andExpect(jsonPath("$.data.candidaturesEnCours").exists())
        .andExpect(jsonPath("$.data.finContratDans7Jours").exists());
  }

  @Test
  void adminVoitFinContratDans7Jours() throws Exception {
    UUID cdd1Id = UUID.randomUUID();
    // CDD 1 : notification 'planifiee' (pas encore envoyée) -> DOIT être compté
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, date_fin_contrat_prevue, statut)
        values (?::uuid, 'Interim', 'Alpha', 'cdd1@test.ma', ?::uuid, ?::uuid,
          ?, 'CDD', ?, 'actif')
        """,
        cdd1Id,
        departementId,
        managerId,
        LocalDate.now().minusMonths(5),
        LocalDate.now().plusDays(3));

    jdbcTemplate.update(
        """
        insert into notifications_planifiees(id, employe_id, type_surveillance, date_echeance, statut)
        values (?::uuid, ?::uuid, 'fin_cdd', ?, 'planifiee')
        """,
        UUID.randomUUID(),
        cdd1Id,
        LocalDate.now().plusDays(2));

    UUID cdd2Id = UUID.randomUUID();
    // CDD 2 : notification déjà 'envoyee' (documents déjà envoyés) -> NE DOIT PAS être compté
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, date_fin_contrat_prevue, statut)
        values (?::uuid, 'Autre', 'Beta', 'cdd2@test.ma', ?::uuid, ?::uuid,
          ?, 'CDD', ?, 'actif')
        """,
        cdd2Id,
        departementId,
        managerId,
        LocalDate.now().minusMonths(5),
        LocalDate.now().plusDays(3));

    jdbcTemplate.update(
        """
        insert into notifications_planifiees(id, employe_id, type_surveillance, date_echeance, statut)
        values (?::uuid, ?::uuid, 'fin_cdd', ?, 'envoyee')
        """,
        UUID.randomUUID(),
        cdd2Id,
        LocalDate.now().plusDays(2));

    mockMvc
        .perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finContratDans7Jours").value(1));
  }

  // ─── Tests Manager (EF-DASH-02) ────────────────────────────────────────────

  @Test
  void managerVoitStatsDeSonDepartement() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/stats/manager").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.employesActifs").value(2))
        .andExpect(jsonPath("$.data.demandesEnAttente").value(1))
        .andExpect(jsonPath("$.data.anomaliesDuJour").value(1))
        // Vue Manager : pas de répartition / candidatures / fins de contrat
        .andExpect(jsonPath("$.data.repartitionParDepartement").doesNotExist())
        .andExpect(jsonPath("$.data.candidaturesEnCours").doesNotExist())
        .andExpect(jsonPath("$.data.finContratDans7Jours").doesNotExist());
  }

  @Test
  void managerNePeutPasAccederStatsAdmin() throws Exception {
    // /api/dashboard/stats est Admin-only (ou délégué actif) — un Manager brut doit recevoir 403
    mockMvc
        .perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  // ─── RBAC : non-authentifié ─────────────────────────────────────────────────

  @Test
  void nonAuthentifieForbidden() throws Exception {
    // ApiAuthenticationEntryPoint (SecurityConfig) répond 401 pour un principal anonyme.
    mockMvc.perform(get("/api/dashboard/stats")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/dashboard/stats/manager")).andExpect(status().isUnauthorized());
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private void insertEmploye(UUID id, String statut, UUID deptId) {
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, statut)
        values (?::uuid, 'Employe', 'Test', ?::text, ?::uuid, ?::uuid, ?, 'CDI', ?::statut_actif_inactif)
        """,
        id,
        id + "@test.ma",
        deptId,
        managerId,
        LocalDate.now().minusYears(1),
        statut);
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

  private String login(String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(email, "TestPass@2026"))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .at("/data/token")
        .asText();
  }
}
