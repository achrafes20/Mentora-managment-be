package ma.hbdev.rh.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.SessionRepository;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class EmployeIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DepartementRepository departementRepository;
  @Autowired private EmployeTransfertRepository transfertRepository;
  @Autowired private EmployeDocumentRepository documentRepository;
  @Autowired private EmployeRepository employeRepository;
  @Autowired private EmployeService employeService;
  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID departementId;
  private UUID autreDepartementId;
  private String adminToken;
  private String managerToken;

  @BeforeEach
  void authentifierEtCreerDepartements() throws Exception {
    nettoyerTracesTransverses();
    // Ordre imposé par les FK effectue_par/televerse_par/manager_id -> utilisateurs (T1.C1) : les
    // tables qui référencent un utilisateur doivent être vidées avant de pouvoir supprimer les
    // utilisateurs des tests précédents.
    transfertRepository.deleteAll();
    documentRepository.deleteAll();
    // fichiers.televerse_par -> utilisateurs : FichierRepository est package-private dans
    // shared/file (T1.B1, non consommé par d'autres modules), suppression directe en SQL ici.
    jdbcTemplate.update("DELETE FROM fichiers");
    employeRepository.deleteAll();
    departementRepository.deleteAll();
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

    adminToken = login("admin@hbdev.ma", "AdminPass@2025");
    managerToken = login("manager@hbdev.ma", "ManagerPass@2025");

    // departementId est géré par le Manager de test (EF-AUTH-03) ; autreDepartementId ne l'est
    // pas, pour vérifier le périmètre.
    departementId =
        departementRepository
            .save(new Departement("Ingenierie " + UUID.randomUUID(), manager.getId()))
            .getId();
    autreDepartementId =
        departementRepository.save(new Departement("Ventes " + UUID.randomUUID(), null)).getId();
  }

  private void nettoyerTracesTransverses() {
    // notifications_planifiees référence employe_id (FK) : sans ce nettoyage, tout employé encore
    // référencé (CDD/stage avec une date de fin future ayant déclenché une notification planifiée
    // via SurveillancePlanifieeService) bloque le employeRepository.deleteAll() du test suivant.
    jdbcTemplate.execute(
        "TRUNCATE TABLE notifications_mattermost, notifications_in_app, journal_audit,"
            + " notifications_planifiees");
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

  private String requeteCreation(String email, String typeContrat, String dateFinContratPrevue)
      throws Exception {
    return requeteCreationPourDepartement(email, typeContrat, dateFinContratPrevue, departementId);
  }

  private String requeteCreationPourDepartement(
      String email, String typeContrat, String dateFinContratPrevue, UUID cibleDepartementId) {
    return """
        {"nom":"Dupont","prenom":"Jean","email":"%s","telephone":"0600000000","poste":"Dev",
         "departementId":"%s","dateEmbauche":"2024-01-15","typeContrat":"%s",
         "dateFinContratPrevue":%s}
        """
        .formatted(
            email,
            cibleDepartementId,
            typeContrat,
            dateFinContratPrevue == null ? "null" : "\"" + dateFinContratPrevue + "\"");
  }

  private String requeteCreationAvecFinStage(
      String email, String typeContrat, String dateFinStagePrevue) {
    return """
        {"nom":"Dupont","prenom":"Jean","email":"%s","telephone":"0600000000","poste":"Dev",
         "departementId":"%s","dateEmbauche":"2024-01-15","typeContrat":"%s",
         "dateFinStagePrevue":%s}
        """
        .formatted(
            email,
            departementId,
            typeContrat,
            dateFinStagePrevue == null ? "null" : "\"" + dateFinStagePrevue + "\"");
  }

  @Test
  void creeListeModifieEtTransfereUnEmploye() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("jean.dupont@test.ma", "CDI", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nom").value("Dupont"))
            .andExpect(
                jsonPath("$.data.departementNom")
                    .value(org.hamcrest.Matchers.startsWith("Ingenierie")))
            .andExpect(jsonPath("$.data.statut").value("actif"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(
            get("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .param("recherche", "Dupont"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].nom").value("Dupont"));

    String requeteModif =
        """
        {"nom":"Dupont","prenom":"Jean-Marc","email":"jean.dupont@test.ma","telephone":"0600000000",
         "poste":"Lead Dev","dateEmbauche":"2024-01-15","typeContrat":"CDI","dateFinContratPrevue":null}
        """;
    mockMvc
        .perform(
            put("/api/employes/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteModif))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.prenom").value("Jean-Marc"));

    String requeteTransfert =
        """
        {"nouveauDepartementId":"%s","dateEffet":"2024-06-01"}
        """
            .formatted(autreDepartementId);
    mockMvc
        .perform(
            post("/api/employes/{id}/transferer", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteTransfert))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.departementId").value(autreDepartementId.toString()));

    mockMvc
        .perform(
            get("/api/employes/{id}/transferts", id)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].nouveauDepartementId").value(autreDepartementId.toString()));
  }

  @Test
  void creeUnEmployeAvecSexeEtLeRestitueDansLaReponse() throws Exception {
    String requete =
        """
        {"nom":"Bennani","prenom":"Fatima","email":"fatima.bennani@test.ma",
         "departementId":"%s","dateEmbauche":"2024-01-15","typeContrat":"CDI","sexe":"FEMME"}
        """
            .formatted(departementId);

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.sexe").value("FEMME"));
  }

  @Test
  void creeUnEmployeAvecCinEtLaRestitueDansLaReponse() throws Exception {
    String requete =
        """
        {"nom":"Idrissi","prenom":"Youssef","email":"youssef.idrissi@test.ma",
         "departementId":"%s","dateEmbauche":"2024-01-15","typeContrat":"CDI","cin":"AB123456"}
        """
            .formatted(departementId);

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.cin").value("AB123456"));
  }

  @Test
  void refuseUnEmailDejaUtilise() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("dup@test.ma", "CDI", null)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("dup@test.ma", "CDI", null)))
        .andExpect(status().isConflict());
  }

  @Test
  void accepteDateFinContratSurCddEtLaRefuseAilleurs() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("cdd@test.ma", "CDD", "2024-12-31")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dateFinContratPrevue").value("2024-12-31"));

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("cdi-avec-date@test.ma", "CDI", "2024-12-31")))
        .andExpect(status().isBadRequest());
  }

  // EF-DOC-12 : dateFinStagePrevue est le pendant de dateFinContratPrevue pour les stagiaires
  // (champ dédié — cf. V14 — car dateFinContratPrevue reste réservé aux CDD).
  @Test
  void accepteDateFinStageSurStagiaireEtLaRefuseAilleurs() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    requeteCreationAvecFinStage("stagiaire@test.ma", "STAGIAIRE", "2024-08-31")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dateFinStagePrevue").value("2024-08-31"));

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    requeteCreationAvecFinStage(
                        "stagiaire-remunere@test.ma", "STAGIAIRE_REMUNERE", "2024-08-31")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dateFinStagePrevue").value("2024-08-31"));

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    requeteCreationAvecFinStage("cdi-avec-fin-stage@test.ma", "CDI", "2024-08-31")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    requeteCreationAvecFinStage("cdd-avec-fin-stage@test.ma", "CDD", "2024-08-31")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void desactiveUnEmployeAvecMotifEtDateDepart() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("depart@test.ma", "CDI", null)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(
            post("/api/employes/{id}/desactiver", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motif":"demission","dateDepart":"2024-07-01"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/employes/{id}", id).header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.data.statut").value("inactif"))
        .andExpect(jsonPath("$.data.motifDepart").value("demission"));
  }

  @Test
  void bloqueLaDesactivationDuDepartementTantQuUnEmployeActifYEstRattache() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("actif@test.ma", "CDI", null)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/departements/{id}", departementId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict());
  }

  @Test
  void attacheEtListeUnDocument() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("doc@test.ma", "CDI", null)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    MockMultipartFile fichier =
        new MockMultipartFile(
            "fichier", "contrat.pdf", "application/pdf", "contenu-test".getBytes());
    MockMultipartFile typeDocument =
        new MockMultipartFile("typeDocument", "", "text/plain", "contrat".getBytes());

    String reponseDocument =
        mockMvc
            .perform(
                multipart("/api/employes/{id}/documents", id)
                    .file(fichier)
                    .file(typeDocument)
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nomOriginal").value("contrat.pdf"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = objectMapper.readTree(reponseDocument).at("/data/id").asText();

    mockMvc
        .perform(
            get("/api/employes/{id}/documents", id).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].typeDocument").value("contrat"));

    mockMvc
        .perform(
            get("/api/employes/{id}/documents/{documentId}/telecharger", id, documentId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(content().bytes("contenu-test".getBytes()))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("contrat.pdf")));
  }

  // Cf. DepartementIntegrationTest#requeteAnonymeEstRefusee : pas d'AuthenticationEntryPoint
  // personnalisé -> Http403ForbiddenEntryPoint par défaut, donc 403 pour un principal anonyme.
  @Test
  void requeteAnonymeEstRefusee() throws Exception {
    mockMvc.perform(get("/api/employes")).andExpect(status().isForbidden());
  }

  @Test
  void managerNePeutPasCreerModifierTransfererOuDesactiverUnEmploye() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("perimetre@test.ma", "CDI", null)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(
            post("/api/employes")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("refuse@test.ma", "CDI", null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/employes/{id}", id)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("refuse@test.ma", "CDI", null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/employes/{id}/desactiver", id)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motif":"demission","dateDepart":"2024-07-01"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerPeutConsulterLesEmployesDeSonPropreDepartement() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("equipe@test.ma", "CDI", null)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(get("/api/employes/{id}", id).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("equipe@test.ma"));

    mockMvc
        .perform(get("/api/employes").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].email").value("equipe@test.ma"))
        .andExpect(jsonPath("$.data.content.length()").value(1));
  }

  @Test
  void managerNePeutPasConsulterUnEmployeHorsDeSonDepartement() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        requeteCreationPourDepartement(
                            "horsperimetre@test.ma", "CDI", null, autreDepartementId)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(get("/api/employes/{id}", id).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  // EF-EXP-01
  @Test
  void exporteLaListeDesEmployesEnExcelAvecLesMemesFiltresQueLaListe() throws Exception {
    mockMvc.perform(
        post("/api/employes")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requeteCreation("export.xlsx@test.ma", "CDI", null)));

    byte[] corps =
        mockMvc
            .perform(
                get("/api/employes/export")
                    .header("Authorization", "Bearer " + adminToken)
                    .param("format", "xlsx")
                    .param("recherche", "export.xlsx"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("employes_")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(corps))) {
      var feuille = classeur.getSheetAt(0);
      assertThat(feuille.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Email");
      Row ligne = feuille.getRow(1);
      assertThat(ligne.getCell(2).getStringCellValue()).isEqualTo("export.xlsx@test.ma");
    }
  }

  @Test
  void exporteLaListeDesEmployesEnPdf() throws Exception {
    mockMvc.perform(
        post("/api/employes")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requeteCreation("export.pdf@test.ma", "CDI", null)));

    byte[] corps =
        mockMvc
            .perform(
                get("/api/employes/export")
                    .header("Authorization", "Bearer " + adminToken)
                    .param("format", "pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(corps).isNotEmpty();
    assertThat(new String(corps, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
  }

  @Test
  void managerNExporteQueSonPropreDepartement() throws Exception {
    mockMvc.perform(
        post("/api/employes")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requeteCreation("equipe.export@test.ma", "CDI", null)));
    mockMvc.perform(
        post("/api/employes")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                requeteCreationPourDepartement(
                    "horsperimetre.export@test.ma", "CDI", null, autreDepartementId)));

    byte[] corps =
        mockMvc
            .perform(
                get("/api/employes/export")
                    .header("Authorization", "Bearer " + managerToken)
                    .param("format", "xlsx"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(corps))) {
      var feuille = classeur.getSheetAt(0);
      assertThat(feuille.getLastRowNum()).isEqualTo(1);
      assertThat(feuille.getRow(1).getCell(2).getStringCellValue())
          .isEqualTo("equipe.export@test.ma");
    }
  }

  @Test
  void desactiveAutomatiquementUnCddDontLaDateDeFinEstDepassee() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("cdd.expire@test.ma", "CDD", "2020-01-31")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(reponseCreation).at("/data/id").asText());

    employeService.desactiverContratsExpires();

    Employe employe = employeRepository.findById(id).orElseThrow();
    assertThat(employe.getStatut()).isEqualTo(StatutActifInactif.inactif);
    assertThat(employe.getMotifDepart()).isEqualTo(MotifDepartEmploye.fin_cdd);
    assertThat(employe.getDateDepart()).isEqualTo(java.time.LocalDate.of(2020, 1, 31));
  }

  @Test
  void desactiveAutomatiquementUnStageDontLaDateDeFinEstDepassee() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        requeteCreationAvecFinStage(
                            "stage.expire@test.ma", "STAGIAIRE", "2020-03-15")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(reponseCreation).at("/data/id").asText());

    employeService.desactiverContratsExpires();

    Employe employe = employeRepository.findById(id).orElseThrow();
    assertThat(employe.getStatut()).isEqualTo(StatutActifInactif.inactif);
    assertThat(employe.getMotifDepart()).isEqualTo(MotifDepartEmploye.fin_stage);
    assertThat(employe.getDateDepart()).isEqualTo(java.time.LocalDate.of(2020, 3, 15));
  }

  @Test
  void neDesactivePasUnCddDontLaDateDeFinEstFuture() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("cdd.futur@test.ma", "CDD", "2099-12-31")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(reponseCreation).at("/data/id").asText());

    employeService.desactiverContratsExpires();

    Employe employe = employeRepository.findById(id).orElseThrow();
    assertThat(employe.getStatut()).isEqualTo(StatutActifInactif.actif);
  }

  @Test
  void desactiveImmediatementQuandUneModificationRendUneDateFinDeContratPassee() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("cdd.corrige@test.ma", "CDD", "2099-12-31")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(reponseCreation).at("/data/id").asText());

    String requeteModif =
        """
        {"nom":"Dupont","prenom":"Jean","email":"cdd.corrige@test.ma","telephone":"0600000000",
         "poste":"Dev","dateEmbauche":"2024-01-15","typeContrat":"CDD","dateFinContratPrevue":"2020-05-31"}
        """;

    mockMvc
        .perform(
            put("/api/employes/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteModif))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("inactif"));

    Employe employe = employeRepository.findById(id).orElseThrow();
    assertThat(employe.getStatut()).isEqualTo(StatutActifInactif.inactif);
    assertThat(employe.getMotifDepart()).isEqualTo(MotifDepartEmploye.fin_cdd);
    assertThat(employe.getDateDepart()).isEqualTo(java.time.LocalDate.of(2020, 5, 31));
  }
}
