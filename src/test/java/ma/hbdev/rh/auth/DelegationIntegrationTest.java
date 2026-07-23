package ma.hbdev.rh.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.recruitment.OffreEmploiRequete;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T4.B1 — EF-AUTH-11→15. Réplique le pattern de {@code UserIntegrationTest}/ {@code
 * RecruitmentIntegrationTest} : RBAC, validation métier, et effet cross-module sur les endpoints
 * recrutement et demandes administratives (délégué actif == Admin pour ces actions), marquage
 * d'audit EF-AUTH-14 et diffusion Mattermost EF-AUTH-15 — testés ici plutôt que dans {@code
 * AdministrativeIntegrationTest}/{@code RecruitmentIntegrationTest} : ce sont des effets de la
 * délégation, pas du module cible (même précédent que le bloc recrutement ci-dessous).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DelegationIntegrationTest {

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
  @Autowired private DelegationService delegationService;

  @MockitoBean private MattermostClient mattermostClient;

  private UUID departementId;
  private UUID employeId;
  private UUID managerId;
  private UUID autreManagerId;
  private String adminToken;
  private String managerToken;
  private String autreManagerToken;

  @BeforeEach
  void setUp() throws Exception {
    when(mattermostClient.envoyerMessagePrive(any(UUID.class), anyString()))
        .thenReturn(ResultatMattermost.succes());
    // Ordre CASCADE : journal_audit référence delegations_approbation (delegation_id, EF-AUTH-14) —
    // un DELETE simple sur delegations_approbation casserait la FK dès qu'une ligne est marquée.
    jdbcTemplate.execute(
        """
        truncate table notifications_mattermost, notifications_in_app, journal_audit,
          mouvements_conges, demandes_administratives, employes, offres_emploi,
          delegations_approbation, departements, sessions_utilisateur, utilisateurs
        cascade
        """);

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
    manager = userRepository.save(manager);
    managerId = manager.getId();

    User autreManager = new User();
    autreManager.setEmail("autre.manager@hbdev.ma");
    autreManager.setMotDePasseHash(passwordEncoder.encode("ManagerPass@2025"));
    autreManager.setRole(RoleUtilisateur.manager);
    autreManager.setNom("Alaoui");
    autreManager.setPrenom("Sara");
    autreManager = userRepository.save(autreManager);
    autreManagerId = autreManager.getId();

    adminToken = login("admin@hbdev.ma", "AdminPass@2025");
    managerToken = login("manager@hbdev.ma", "ManagerPass@2025");
    autreManagerToken = login("autre.manager@hbdev.ma", "ManagerPass@2025");

    departementId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO departements (id, nom, manager_id) VALUES (?, ?, ?)",
        departementId,
        "Ingenierie " + UUID.randomUUID(),
        managerId);

    employeId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, statut)
        values (?, ?, ?, ?, ?, ?, ?, 'CDI', 'actif')
        """,
        employeId,
        "Benali",
        "Yassine",
        "yassine-" + UUID.randomUUID() + "@test.ma",
        departementId,
        managerId,
        LocalDate.now().minusYears(1));
  }

  private String creerDemandeBonDeSortie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/demandes-administratives")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeId":"%s","typeDemande":"bon_sortie","dateDebut":"%s",
                         "heureDepart":"10:00","heureRetourPrevue":"12:00"}
                        """
                            .formatted(employeId, LocalDate.now().plusDays(1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statut").value("en_attente"))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
  }

  private Map<String, Object> ligneAudit(UUID entiteId, String action) {
    return jdbcTemplate.queryForMap(
        "select en_delegation, delegation_id from journal_audit"
            + " where entite_id = ? and action = ? order by horodatage desc limit 1",
        entiteId,
        action);
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

  private ResultActions creerOffreCommeManager(String token) throws Exception {
    String requete =
        objectMapper.writeValueAsString(
            new OffreEmploiRequete(
                "Développeur backend", "Description", departementId, List.of("java")));
    return mockMvc.perform(
        post("/api/offres")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requete));
  }

  private String creerDelegation(UUID delegueId, LocalDate debut, LocalDate fin) throws Exception {
    String requete =
        objectMapper.writeValueAsString(new DelegationCreationRequete(delegueId, debut, fin));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/delegations")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requete))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
  }

  // --- RBAC ---

  @Test
  void managerNePeutPasCreerDeDelegation() throws Exception {
    String requete =
        objectMapper.writeValueAsString(
            new DelegationCreationRequete(
                autreManagerId, LocalDate.now(), LocalDate.now().plusDays(7)));
    mockMvc
        .perform(
            post("/api/delegations")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerNePeutPasListerLesDelegations() throws Exception {
    mockMvc
        .perform(get("/api/delegations").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  // --- Validation métier ---

  @Test
  void refuseLaDelegationASoiMeme() throws Exception {
    UUID adminId = extraireIdParEmail("admin@hbdev.ma");

    String requete =
        objectMapper.writeValueAsString(
            new DelegationCreationRequete(adminId, LocalDate.now(), LocalDate.now().plusDays(7)));
    mockMvc
        .perform(
            post("/api/delegations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isBadRequest());
  }

  private UUID extraireIdParEmail(String email) {
    return userRepository.findByEmail(email).orElseThrow().getId();
  }

  @Test
  void refuseUnDelegueInconnu() throws Exception {
    String requete =
        objectMapper.writeValueAsString(
            new DelegationCreationRequete(
                UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(7)));
    mockMvc
        .perform(
            post("/api/delegations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isNotFound());
  }

  @Test
  void refuseUneDateFinAnterieureALaDateDebut() throws Exception {
    String requete =
        objectMapper.writeValueAsString(
            new DelegationCreationRequete(
                managerId, LocalDate.now(), LocalDate.now().minusDays(1)));
    mockMvc
        .perform(
            post("/api/delegations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isBadRequest());
  }

  // --- Effet cross-module sur le recrutement ---

  @Test
  void sansDelegationUnManagerNePeutPasCreerDOffre() throws Exception {
    creerOffreCommeManager(managerToken).andExpect(status().isForbidden());
  }

  @Test
  void unDelegueActifPeutCreerUneOffreCommeUnAdmin() throws Exception {
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    creerOffreCommeManager(managerToken).andExpect(status().isCreated());
  }

  @Test
  void unManagerSansDelegationResteRefuseMemeSiUnAutreEstDelegue() throws Exception {
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    creerOffreCommeManager(autreManagerToken).andExpect(status().isForbidden());
  }

  @Test
  void laRevocationRetireImmediatementLesDroitsDuDelegue() throws Exception {
    String delegationId = creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            post("/api/delegations/" + delegationId + "/revoquer")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("revoquee"));

    creerOffreCommeManager(managerToken).andExpect(status().isForbidden());
  }

  @Test
  void uneDelegationPasEncoreDemarreePeutEtreRevoqueeAvantSonDebut() throws Exception {
    // EF-AUTH-13 : "revocable manuellement a tout moment... avant l'echeance" — y compris avant
    // dateDebut. estEffectivementActive() exigerait a tort que la periode ait deja commence ;
    // repro du bug observe en E2E (revocation refusee avec "n'est plus active" sur une delegation
    // planifiee pour le lendemain).
    String delegationId =
        creerDelegation(managerId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            post("/api/delegations/" + delegationId + "/revoquer")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("revoquee"));
  }

  @Test
  void uneDelegationEchueEstPresenteeCommeExpireeSansAccorderDeDroits() throws Exception {
    // date_fin dans le passé : accepté à la création (pas de règle bloquant les dates passées),
    // mais le statut effectif (calculé à la lecture, EF-AUTH-13) doit refléter l'expiration.
    creerDelegation(managerId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));

    mockMvc
        .perform(get("/api/delegations").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].statut").value("expiree"));

    creerOffreCommeManager(managerToken).andExpect(status().isForbidden());
  }

  @Test
  void unManagerNePeutPasListerLHistoriqueMaisPeutVoirSaPropreDelegationActive() throws Exception {
    // Bug E2E reproduit : le frontend n'avait aucun moyen de savoir qu'un Manager est délégué
    // actif (GET /api/delegations est reservé Admin), donc les boutons de décision restaient
    // masqués côté UI malgré une délégation réellement active côté backend. GET /moi comble ça
    // sans exposer l'historique complet (toujours 403 pour un Manager) à un non-Admin.
    String delegationId = creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(get("/api/delegations").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/delegations/moi").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(delegationId))
        .andExpect(jsonPath("$.data.statut").value("active"));

    mockMvc
        .perform(get("/api/delegations/moi").header("Authorization", "Bearer " + autreManagerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void unDelegueActifPeutListerLesManagersPourUnSelecteurMaisPasLaListeComplete() throws Exception {
    // Bug E2E reproduit : SecurityConfig bloque tout /api/users/** derrière hasRole("ADMIN") au
    // niveau de la chaîne de filtres, *avant* même que le @PreAuthorize méthode de
    // UserController#listerManagers() ne soit évalué — contrairement à /api/delegations, qui n'a
    // pas cette règle d'URL séparée. L'exception ciblée sur /api/users/managers dans SecurityConfig
    // est ce qui rend le délégué actif capable d'atteindre le contrôleur.
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(get("/api/users/managers").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/users").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/users/managers").header("Authorization", "Bearer " + autreManagerToken))
        .andExpect(status().isForbidden());
  }

  // --- Effet cross-module sur le périmètre des candidatures (EF-AUTH-11/12) ---

  @Test
  void unDelegueActifVoitEtOuvreLesCandidaturesHorsDeSonPerimetre() throws Exception {
    // Bug E2E du 2026-07-23 : CandidatureService.lister()/verifierPerimetreManager()
    // restreignaient un Manager aux seules candidatures pour lesquelles UN ENTRETIEN LUI EST DEJA
    // ASSIGNE — jamais mis à jour pour la délégation, contrairement aux endpoints de décision
    // (changerStatut, etc.) déjà délégables depuis T3.B1/T4.B1.
    mockMvc
        .perform(
            post("/api/offres")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new OffreEmploiRequete(
                            "Développeur backend", "Description", departementId, List.of("java")))))
        .andExpect(status().isCreated());

    MvcResult ingestion =
        mockMvc
            .perform(
                multipart("/api/recruitment/ingest")
                    .header("X-Internal-Webhook-Secret", "test-secret-integration")
                    .param("emailExpediteur", "perimetre-candidature@test.ma")
                    .param("sujet", "Candidature Développeur backend"))
            .andExpect(status().isCreated())
            .andReturn();
    String candidatureId =
        objectMapper.readTree(ingestion.getResponse().getContentAsString()).at("/data/id").asText();

    // Sans délégation : aucun entretien assigné à managerToken -> invisible en liste et en détail.
    mockMvc
        .perform(get("/api/candidatures").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));

    mockMvc
        .perform(
            get("/api/candidatures/" + candidatureId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(get("/api/candidatures").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/candidatures/" + candidatureId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
  }

  @Test
  void unRejetDeCandidatureParUnDelegueEstMarqueEnDelegationDansLeJournalAudit() throws Exception {
    mockMvc
        .perform(
            post("/api/offres")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new OffreEmploiRequete(
                            "Développeur backend", "Description", departementId, List.of("java")))))
        .andExpect(status().isCreated());

    MvcResult ingestion =
        mockMvc
            .perform(
                multipart("/api/recruitment/ingest")
                    .header("X-Internal-Webhook-Secret", "test-secret-integration")
                    .param("emailExpediteur", "audit-recrutement@test.ma")
                    .param("sujet", "Candidature Développeur backend"))
            .andExpect(status().isCreated())
            .andReturn();
    String candidatureId =
        objectMapper.readTree(ingestion.getResponse().getContentAsString()).at("/data/id").asText();

    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            post("/api/candidatures/" + candidatureId + "/statut")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"rejete\"}"))
        .andExpect(status().isOk());

    Map<String, Object> ligne = ligneAudit(UUID.fromString(candidatureId), "statut_rejete");
    assertThat(ligne.get("en_delegation")).isEqualTo(true);
  }

  // --- Effet cross-module sur les demandes administratives (EF-AUTH-11/12, T3.A2) ---

  @Test
  void sansDelegationUnManagerNePeutPasApprouverUneDemandeAdministrative() throws Exception {
    String demandeId = creerDemandeBonDeSortie();

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/approuver")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void unDelegueActifPeutApprouverUneDemandeAdministrative() throws Exception {
    String demandeId = creerDemandeBonDeSortie();
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/approuver")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("approuvee"));
  }

  @Test
  void unDelegueActifPeutRejeterUneDemandeAdministrative() throws Exception {
    String demandeId = creerDemandeBonDeSortie();
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/rejeter")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("rejetee"));
  }

  @Test
  void unManagerSansDelegationResteRefuseMemeSiUnAutreEstDelegueSurLesDemandes() throws Exception {
    String demandeId = creerDemandeBonDeSortie();
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/approuver")
                .header("Authorization", "Bearer " + autreManagerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void unDelegueActifVoitLesDemandesSoldeEtMouvementsHorsDeSonEquipe() throws Exception {
    // Bug E2E du 2026-07-23 : AdministrativeService.lister()/solde()/mouvements() restreignaient
    // un Manager aux seuls employés qu'il gère personnellement (employes.manager_id) — un délégué
    // approuvant pour un employé géré par un AUTRE manager ne verrait jamais la demande dans sa
    // liste, ni le solde/registre nécessaires pour instruire la décision.
    UUID autreEmployeId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into employes(id, nom, prenom, email, departement_id, manager_id,
          date_embauche, type_contrat, statut)
        values (?, ?, ?, ?, ?, ?, ?, 'CDI', 'actif')
        """,
        autreEmployeId,
        "Alami",
        "Sofia",
        "sofia-" + UUID.randomUUID() + "@test.ma",
        departementId,
        autreManagerId,
        LocalDate.now().minusYears(1));

    mockMvc
        .perform(
            post("/api/demandes-administratives")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"employeId":"%s","typeDemande":"bon_sortie","dateDebut":"%s",
                     "heureDepart":"10:00","heureRetourPrevue":"12:00"}
                    """
                        .formatted(autreEmployeId, LocalDate.now().plusDays(1))))
        .andExpect(status().isOk());

    // Sans délégation : managerToken ne gère pas autreEmployeId -> invisible en liste, refusé sur
    // solde/mouvements.
    mockMvc
        .perform(
            get("/api/demandes-administratives")
                .param("employeId", autreEmployeId.toString())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));

    mockMvc
        .perform(
            get("/api/demandes-administratives/employes/" + autreEmployeId + "/solde")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            get("/api/demandes-administratives")
                .param("employeId", autreEmployeId.toString())
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));

    mockMvc
        .perform(
            get("/api/demandes-administratives/employes/" + autreEmployeId + "/solde")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/demandes-administratives/employes/" + autreEmployeId + "/mouvements")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
  }

  // --- EF-AUTH-14 : marquage du journal d'audit ---

  @Test
  void uneApprobationParUnDelegueEstMarqueeEnDelegationDansLeJournalAudit() throws Exception {
    String demandeId = creerDemandeBonDeSortie();
    String delegationId = creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/approuver")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    Map<String, Object> ligne = ligneAudit(UUID.fromString(demandeId), "approbation");
    assertThat(ligne.get("en_delegation")).isEqualTo(true);
    assertThat(ligne.get("delegation_id")).isEqualTo(UUID.fromString(delegationId));
  }

  @Test
  void uneApprobationParUnAdminSansDelegationNestPasMarqueeEnDelegation() throws Exception {
    String demandeId = creerDemandeBonDeSortie();

    mockMvc
        .perform(
            patch("/api/demandes-administratives/" + demandeId + "/approuver")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    Map<String, Object> ligne = ligneAudit(UUID.fromString(demandeId), "approbation");
    assertThat(ligne.get("en_delegation")).isEqualTo(false);
    assertThat(ligne.get("delegation_id")).isNull();
  }

  // --- EF-AUTH-15 : diffusion Mattermost aux Managers au démarrage/à la fin d'une délégation ---

  @Test
  void laCreationDUneDelegationNotifieTousLesManagersActifsParMattermost() throws Exception {
    creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));

    verify(mattermostClient, times(1)).envoyerMessagePrive(eq(managerId), anyString());
    verify(mattermostClient, times(1)).envoyerMessagePrive(eq(autreManagerId), anyString());
    verify(mattermostClient, times(2)).envoyerMessagePrive(any(UUID.class), anyString());
  }

  @Test
  void laRevocationNotifieLaFinDeDelegationParMattermost() throws Exception {
    String delegationId = creerDelegation(managerId, LocalDate.now(), LocalDate.now().plusDays(7));
    clearInvocations(mattermostClient);

    mockMvc
        .perform(
            post("/api/delegations/" + delegationId + "/revoquer")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    verify(mattermostClient, times(2)).envoyerMessagePrive(any(UUID.class), anyString());
  }

  @Test
  void laPurgeQuotidienneExpireLaDelegationEtNotifieLaFinParMattermost() throws Exception {
    String delegationId =
        creerDelegation(managerId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
    clearInvocations(mattermostClient);

    delegationService.expirerDelegationsEcheues();

    String statutPersiste =
        jdbcTemplate.queryForObject(
            "select statut::text from delegations_approbation where id = ?",
            String.class,
            UUID.fromString(delegationId));
    assertThat(statutPersiste).isEqualTo("expiree");
    verify(mattermostClient, times(2)).envoyerMessagePrive(any(UUID.class), anyString());
  }
}
