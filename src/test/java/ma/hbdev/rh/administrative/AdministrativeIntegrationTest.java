package ma.hbdev.rh.administrative;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
class AdministrativeIntegrationTest {

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

  @MockitoBean private MattermostClient mattermostClient;

  private String adminToken;
  private String managerToken;
  private String autreManagerToken;
  private UUID managerId;
  private UUID autreManagerId;
  private UUID employeId;

  @BeforeEach
  void preparerDonnees() throws Exception {
    when(mattermostClient.envoyerMessagePrive(any(UUID.class), anyString()))
        .thenReturn(ResultatMattermost.succes());
    jdbcTemplate.execute(
        """
        truncate table notifications_mattermost, notifications_in_app, journal_audit,
          mouvements_conges, demandes_administratives, jours_feries, employes,
          departements, sessions_utilisateur, utilisateurs
        cascade
        """);

    String suffixe = UUID.randomUUID().toString();
    User admin = utilisateur("admin-" + suffixe + "@test.ma", RoleUtilisateur.admin);
    User manager = utilisateur("manager-" + suffixe + "@test.ma", RoleUtilisateur.manager);
    User autreManager = utilisateur("manager2-" + suffixe + "@test.ma", RoleUtilisateur.manager);
    managerId = manager.getId();
    autreManagerId = autreManager.getId();
    UUID departementId = UUID.randomUUID();
    employeId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into departements(id, nom, manager_id, statut) values (?::uuid, ?, ?::uuid, 'actif')",
        departementId,
        "RH",
        managerId);
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, statut)
        values (?::uuid, 'Benali', 'Yassine', 'yassine@test.ma', ?::uuid, ?::uuid,
          ?, 'CDI', 'actif')
        """,
        employeId,
        departementId,
        managerId,
        LocalDate.now().minusMonths(10));
    jdbcTemplate.update(
        "insert into mouvements_conges(employe_id, type_mouvement, quantite_jours, commentaire) values (?::uuid, 'initialisation', 5, 'test')",
        employeId);

    adminToken = login(admin.getEmail());
    managerToken = login(manager.getEmail());
    autreManagerToken = login(autreManager.getEmail());
  }

  @Test
  void adminApprouvePuisAnnuleUnCongeAvecLedgerEtNotificationManager() throws Exception {
    // Lundi->mardi (jamais dimanche, seul jour exclu du calcul de duree()) : garantit 2 jours
    // ouvres quel que soit le jour d'execution du test, plutot qu'un plusDays(3)/plusDays(4)
    // hardcode qui echoue des que la fenetre glisse sur un dimanche (cf. echec du 2026-07-22).
    LocalDate debut = prochainLundiAuMoins(3);
    LocalDate fin = debut.plusDays(1);
    String demandeId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/demandes-administratives")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"employeId":"%s","typeDemande":"conge","granularite":"journee",
                                 "dateDebut":"%s","dateFin":"%s","motif":"Repos"}
                                """
                                    .formatted(employeId, debut, fin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statut").value("en_attente"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .at("/data/id")
            .asText();

    mockMvc
        .perform(
            patch("/api/demandes-administratives/{id}/approuver", demandeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("approuvee"));

    BigDecimal consommation =
        jdbcTemplate.queryForObject(
            "select quantite_jours from mouvements_conges where demande_id = ?::uuid and type_mouvement = 'consommation'",
            BigDecimal.class,
            demandeId);
    Integer notifications =
        jdbcTemplate.queryForObject(
            "select count(*) from notifications_in_app where destinataire_id = ?::uuid and entite_id = ?::uuid",
            Integer.class,
            managerId,
            demandeId);
    org.assertj.core.api.Assertions.assertThat(consommation).isEqualByComparingTo("-2");
    org.assertj.core.api.Assertions.assertThat(notifications).isEqualTo(1);

    mockMvc
        .perform(
            patch("/api/demandes-administratives/{id}/annuler", demandeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("annulee"));

    BigDecimal recredit =
        jdbcTemplate.queryForObject(
            "select quantite_jours from mouvements_conges where demande_id = ?::uuid and type_mouvement = 'recredit'",
            BigDecimal.class,
            demandeId);
    org.assertj.core.api.Assertions.assertThat(recredit).isEqualByComparingTo("2");
  }

  @Test
  void managerNePeutPasCreerPourUnEmployeHorsPerimetreNiApprouver() throws Exception {
    mockMvc
        .perform(
            get("/api/demandes-administratives/employes/{id}/solde", employeId)
                .header("Authorization", "Bearer " + autreManagerToken))
        .andExpect(status().isForbidden());

    String demandeId =
        objectMapper
            .readTree(
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
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .at("/data/id")
            .asText();

    mockMvc
        .perform(
            patch("/api/demandes-administratives/{id}/approuver", demandeId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void joursFeriesSontGeresEtExclusDuCalculDeDuree() throws Exception {
    // Meme raisonnement que ci-dessus : un lundi garantit que ferie et ferie+1 (mardi) ne tombent
    // jamais un dimanche, quel que soit le jour d'execution du test.
    LocalDate ferie = prochainLundiAuMoins(8);
    mockMvc
        .perform(
            post("/api/demandes-administratives/jours-feries")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dateFerie":"%s","libelle":"Aid test"}
                    """
                        .formatted(ferie)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/demandes-administratives")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"employeId":"%s","typeDemande":"conge","granularite":"journee",
                     "dateDebut":"%s","dateFin":"%s","motif":"Repos"}
                    """
                        .formatted(employeId, ferie, ferie.plusDays(1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dureeJours").value(1));
  }

  /** Premier lundi a au moins {@code joursMinimum} jours de calendrier a partir d'aujourd'hui. */
  private LocalDate prochainLundiAuMoins(int joursMinimum) {
    LocalDate date = LocalDate.now().plusDays(joursMinimum);
    while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
      date = date.plusDays(1);
    }
    return date;
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
