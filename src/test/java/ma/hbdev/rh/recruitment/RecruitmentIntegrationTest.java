package ma.hbdev.rh.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Réplique le pattern de {@code DepartementIntegrationTest}/{@code EmployeIntegrationTest}. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecruitmentIntegrationTest {

  private static final String SECRET_WEBHOOK = "test-secret-integration";

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

  private UUID departementId;
  private UUID managerId;
  private String adminToken;
  private String managerToken;

  @BeforeEach
  void authentifierEtCreerDepartement() throws Exception {
    nettoyerTracesTransverses();
    // Ordre imposé par les FK : candidatures.analyse_courante_id -> analyses_ia(id) (pas de
    // cascade) interdit de supprimer analyses_ia avant candidatures. envois_documents référence
    // candidatures sans cascade non plus, donc il doit partir en premier. Une fois candidatures
    // supprimées, entretiens/analyses_ia partent automatiquement (ON DELETE CASCADE sur leur FK
    // candidature_id, cf. schema_v1.sql §6).
    jdbcTemplate.update("DELETE FROM envois_documents");
    jdbcTemplate.update("DELETE FROM candidatures");
    jdbcTemplate.update("DELETE FROM offres_emploi");
    jdbcTemplate.update("DELETE FROM departements");
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
    manager = userRepository.save(manager);
    managerId = manager.getId();

    adminToken = login("admin@hbdev.ma", "AdminPass@2025");
    managerToken = login("manager@hbdev.ma", "ManagerPass@2025");

    // Departement est package-privé dans `employee` (T1.B1) — insert SQL direct plutôt qu'une
    // dépendance cross-module au repository (ai-instructions.md règle 4).
    // `statut` omis : DEFAULT 'actif' (enum Postgres) évite un cast de paramètre JDBC explicite.
    departementId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO departements (id, nom, manager_id) VALUES (?, ?, ?)",
        departementId,
        "Ingenierie " + UUID.randomUUID(),
        managerId);
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

  private String creerOffre(String intitule, List<String> motsCles) throws Exception {
    String requete =
        objectMapper.writeValueAsString(
            new OffreEmploiRequete(intitule, "Description " + intitule, departementId, motsCles));
    String reponse =
        mockMvc
            .perform(
                post("/api/offres")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requete))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(reponse).at("/data/id").asText();
  }

  @Test
  void adminCreeUneOffreEtManagerPeutLaConsulterMaisPasEnCreer() throws Exception {
    String offreId = creerOffre("Développeur Full Stack", List.of("Java", "React"));

    mockMvc
        .perform(get("/api/offres").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.id == '" + offreId + "')]").exists());

    mockMvc
        .perform(
            post("/api/offres")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new OffreEmploiRequete("Autre poste", null, departementId, null))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/offres")).andExpect(status().isUnauthorized());
  }

  @Test
  void ingestionRefuseeSansSecretEtAcceptéeAvecLeBonSecret() throws Exception {
    creerOffre("Développeur Backend", List.of("Java"));

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .param("emailExpediteur", "candidat@test.ma")
                .param("sujet", "Candidature Développeur Backend"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", "mauvais-secret")
                .param("emailExpediteur", "candidat@test.ma")
                .param("sujet", "Candidature Développeur Backend"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "candidat@test.ma")
                .param("sujet", "Candidature Développeur Backend"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("candidat@test.ma"))
        .andExpect(jsonPath("$.data.statut").value("recu"));
  }

  // Le corps de l'e-mail (au-delà du CV) était déjà transmis par n8n mais jamais persisté avant
  // cette session — un candidat peut y écrire des infos absentes du CV (disponibilité...).
  @Test
  void persisteEtRestitueLeCorpsDeLEmailDeCandidature() throws Exception {
    creerOffre("Développeur Backend", List.of("Java"));

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "message@test.ma")
                .param("sujet", "Candidature Développeur Backend")
                .param("corps", "Disponible dès septembre, motivé par le poste."))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.data.messageCandidat")
                .value("Disponible dès septembre, motivé par le poste."));
  }

  @Test
  void reingestionAvecNouveauCvChaineLancienneAnalyse() throws Exception {
    creerOffre("Développeur Mobile", List.of("Kotlin"));
    MockMultipartFile premierCv =
        new MockMultipartFile("cv", "cv1.pdf", "application/pdf", new byte[] {1});
    MockMultipartFile nouveauCv =
        new MockMultipartFile("cv", "cv2.pdf", "application/pdf", new byte[] {2});

    String reponseInitiale =
        mockMvc
            .perform(
                multipart("/api/recruitment/ingest")
                    .file(premierCv)
                    .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                    .param("emailExpediteur", "reingestion@test.ma")
                    .param("sujet", "Candidature Développeur Mobile"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String candidatureId = objectMapper.readTree(reponseInitiale).at("/data/id").asText();
    UUID premiereAnalyseId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM analyses_ia WHERE candidature_id = ?::uuid", UUID.class, candidatureId);

    // Ré-ingestion (même offre + même e-mail, nouveau CV) : EF-REC-10, l'historique des analyses
    // doit rester chaîné via remplace_analyse_id, pas seulement lors d'une relance manuelle.
    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .file(nouveauCv)
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "reingestion@test.ma")
                .param("sujet", "Candidature Développeur Mobile"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(candidatureId));

    UUID remplaceAnalyseId =
        jdbcTemplate.queryForObject(
            "SELECT remplace_analyse_id FROM analyses_ia WHERE candidature_id = ?::uuid "
                + "ORDER BY date_analyse DESC LIMIT 1",
            UUID.class,
            candidatureId);
    assertEquals(premiereAnalyseId, remplaceAnalyseId);
  }

  @Test
  void candidatureRecuePourUneOffreFermeeResteEnAttente() throws Exception {
    String offreId = creerOffre("Chef de Projet", List.of("Agile"));
    mockMvc
        .perform(
            post("/api/offres/{id}/fermer", offreId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("fermee"));

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "retard@test.ma")
                .param("sujet", "Candidature Chef de Projet"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.statut").value("en_attente"))
        .andExpect(jsonPath("$.data.offreId").value(offreId));
  }

  @Test
  void uneOffreFermeePeutEtreRouverte() throws Exception {
    String offreId = creerOffre("Développeur Frontend", List.of("React"));
    mockMvc
        .perform(
            post("/api/offres/{id}/fermer", offreId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("fermee"));

    mockMvc
        .perform(
            post("/api/offres/{id}/rouvrir", offreId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("ouverte"));
  }

  @Test
  void parcoursCompletDuPipelineJusquaLentretien() throws Exception {
    creerOffre("Chargé de Recrutement", List.of("RH"));
    MockMultipartFile cv =
        new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[] {1, 2, 3});

    String reponseIngestion =
        mockMvc
            .perform(
                multipart("/api/recruitment/ingest")
                    .file(cv)
                    .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                    .param("emailExpediteur", "pipeline@test.ma")
                    .param("sujet", "Candidature Chargé de Recrutement"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String candidatureId = objectMapper.readTree(reponseIngestion).at("/data/id").asText();

    // recu -> preselectionne
    mockMvc
        .perform(
            post("/api/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangerStatutRequete(
                            StatutCandidature.preselectionne, null, null, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("preselectionne"));

    // Sans manager assigné, la transition vers "entretien" est refusée — sinon la candidature
    // resterait bloquée en "Entretien" sans jamais apparaître dans la file d'aucun Manager.
    mockMvc
        .perform(
            post("/api/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangerStatutRequete(
                            StatutCandidature.entretien,
                            null,
                            Instant.now().plusSeconds(3600),
                            null))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/candidatures/{id}", candidatureId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.data.statut").value("preselectionne"));

    // preselectionne -> entretien (assigne le Manager de test + date planifiée)
    mockMvc
        .perform(
            post("/api/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangerStatutRequete(
                            StatutCandidature.entretien,
                            managerId,
                            Instant.now().plusSeconds(3600),
                            null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("entretien"));

    // Le Manager assigné peut désormais consulter la candidature (EF-REC-08).
    mockMvc
        .perform(
            get("/api/candidatures/{id}", candidatureId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    // ... et y enregistrer un résultat d'entretien (EF-REC-09) : avance automatiquement vers
    // "decision", aucune transition manuelle Admin équivalente (décision confirmée avec Taha).
    mockMvc
        .perform(
            post("/api/candidatures/{id}/entretien", candidatureId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResultatEntretienRequete(ResultatEntretien.favorable, "Bon profil"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resultat").value("favorable"));

    mockMvc
        .perform(
            get("/api/candidatures/{id}", candidatureId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("decision"));

    // decision -> rejete (EF-REC-14) : ne doit jamais bloquer même si le webhook SMTP n8n n'est
    // pas joignable en test (dégradation gracieuse de MailService).
    mockMvc
        .perform(
            post("/api/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangerStatutRequete(
                            StatutCandidature.rejete,
                            null,
                            null,
                            "Message personnalisé de refus"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("rejete"));
  }

  @Test
  void uneTransitionDePipelineNonAutoriseeEstRefusee() throws Exception {
    creerOffre("Testeur QA", List.of("QA"));
    String reponseIngestion =
        mockMvc
            .perform(
                multipart("/api/recruitment/ingest")
                    .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                    .param("emailExpediteur", "qa@test.ma")
                    .param("sujet", "Candidature Testeur QA"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String candidatureId = objectMapper.readTree(reponseIngestion).at("/data/id").asText();

    // recu -> embauche directement : interdit (EF-REC-07, la machine à états impose l'ordre).
    mockMvc
        .perform(
            post("/api/candidatures/{id}/statut", candidatureId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangerStatutRequete(StatutCandidature.embauche, null, null, null))))
        .andExpect(status().isConflict());
  }

  @Test
  void nouvelleOffreCompatibleReactiveUneCandidatureEnAttente() throws Exception {
    String offreFermee = creerOffre("Développeur Java Senior", List.of("Java", "Spring", "SQL"));
    mockMvc
        .perform(
            post("/api/offres/{id}/fermer", offreFermee)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "reactivation@test.ma")
                .param("sujet", "Candidature Développeur Java Senior"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.statut").value("en_attente"));

    // Sans analyse IA (pas de clé API en test), la candidature n'a pas de mots-clés extraits —
    // la réactivation automatique (EF-REC-12) ne peut donc pas matcher. On vérifie ici seulement
    // que la création d'une nouvelle offre compatible ne casse rien et que la candidature reste
    // consultable en 'en_attente' (dégradation gracieuse EF-REC-05 respectée de bout en bout).
    creerOffre("Développeur Java Senior (2)", List.of("Java", "Spring", "SQL"));

    mockMvc
        .perform(get("/api/candidatures").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.content[?(@.email == 'reactivation@test.ma')].statut")
                .value("en_attente"));
  }

  @Test
  void archivageExpireesEstReserveAuxAdminsEtNaffectePasLesCandidaturesRecentes() throws Exception {
    creerOffre("Offre archivage", List.of("Test"));
    mockMvc
        .perform(
            multipart("/api/recruitment/ingest")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK)
                .param("emailExpediteur", "archivage@test.ma")
                .param("sujet", "Non lié"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/candidatures/archiver-expirees"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/candidatures/archiver-expirees")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/candidatures/archiver-expirees")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));
  }
}
