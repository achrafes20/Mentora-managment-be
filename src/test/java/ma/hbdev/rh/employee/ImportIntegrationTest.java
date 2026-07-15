package ma.hbdev.rh.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
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

/** EF-EMP-07 — import Excel/CSV : dry-run, exécution réelle, idempotence, journal des imports. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ImportIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DepartementRepository departementRepository;
  @Autowired private EmployeRepository employeRepository;
  @Autowired private EmployeTransfertRepository transfertRepository;
  @Autowired private EmployeDocumentRepository documentRepository;
  @Autowired private MouvementCongeRepository mouvementCongeRepository;
  @Autowired private ImportLotRepository importLotRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String managerToken;

  @BeforeEach
  void nettoyerEtAuthentifier() throws Exception {
    importLotRepository.deleteAll();
    mouvementCongeRepository.deleteAll();
    transfertRepository.deleteAll();
    documentRepository.deleteAll();
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
    userRepository.save(manager);

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

  private MockMultipartFile fichierCsv(String contenu) {
    return new MockMultipartFile(
        "fichier", "import.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartFile mappingJson(Map<String, Integer> mapping) throws Exception {
    return new MockMultipartFile(
        "mapping", "", "application/json", objectMapper.writeValueAsBytes(mapping));
  }

  @Test
  void importeLesDepartementsEnDryRunPuisReellementEtRejoueSansDoublon() throws Exception {
    String csv = "Nom departement\nIngenierie Import Test\n";

    mockMvc
        .perform(
            multipart("/api/import/analyser")
                .file(fichierCsv(csv))
                .file(mappingJson(Map.of("nom", 0)))
                .param("cible", "DEPARTEMENTS")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mode").value("SIMULATION"))
        .andExpect(jsonPath("$.data.lignesValides").value(1))
        .andExpect(jsonPath("$.data.lignes[0].action").value("CREATION"));

    assertThat(departementRepository.existsByNomIgnoreCase("Ingenierie Import Test")).isFalse();

    mockMvc
        .perform(
            multipart("/api/import/executer")
                .file(fichierCsv(csv))
                .file(mappingJson(Map.of("nom", 0)))
                .param("cible", "DEPARTEMENTS")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mode").value("REEL"))
        .andExpect(jsonPath("$.data.lignes[0].action").value("CREATION"));

    assertThat(departementRepository.existsByNomIgnoreCase("Ingenierie Import Test")).isTrue();

    // Rejouer le même fichier : idempotence, pas de doublon.
    mockMvc
        .perform(
            multipart("/api/import/executer")
                .file(fichierCsv(csv))
                .file(mappingJson(Map.of("nom", 0)))
                .param("cible", "DEPARTEMENTS")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lignes[0].action").value("AUCUN_CHANGEMENT"));

    assertThat(
            departementRepository.findAll().stream()
                .filter(d -> d.getNom().equals("Ingenierie Import Test"))
                .count())
        .isEqualTo(1);

    mockMvc
        .perform(get("/api/import/historique").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3));
  }

  @Test
  void importeLesEmployesEtLesRattacheAuDepartementResoluParNom() throws Exception {
    UUID departementId = departementRepository.save(new Departement("Ingenierie", null)).getId();

    String csv =
        "Nom,Prenom,Email,Departement,DateEmbauche,TypeContrat\n"
            + "Dupont,Jean,jean.import@test.ma,Ingenierie,15/01/2024,CDI\n";
    Map<String, Integer> mapping =
        Map.of(
            "nom", 0,
            "prenom", 1,
            "email", 2,
            "departementNom", 3,
            "dateEmbauche", 4,
            "typeContrat", 5);

    String reponse =
        mockMvc
            .perform(
                multipart("/api/import/executer")
                    .file(fichierCsv(csv))
                    .file(mappingJson(mapping))
                    .param("cible", "EMPLOYES")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lignesValides").value(1))
            .andExpect(jsonPath("$.data.lignes[0].action").value("CREATION"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String employeId = objectMapper.readTree(reponse).at("/data/lignes/0/entiteId").asText();

    mockMvc
        .perform(
            get("/api/employes/{id}", employeId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nom").value("Dupont"))
        .andExpect(jsonPath("$.data.departementId").value(departementId.toString()))
        .andExpect(jsonPath("$.data.departementNom").value("Ingenierie"));

    // Rejouer : mise à jour de la même fiche, pas de doublon.
    mockMvc
        .perform(
            multipart("/api/import/executer")
                .file(fichierCsv(csv))
                .file(mappingJson(mapping))
                .param("cible", "EMPLOYES")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lignes[0].action").value("MISE_A_JOUR"));

    assertThat(employeRepository.count()).isEqualTo(1);
  }

  @Test
  void signaleUneLigneEnErreurQuandLeDepartementEstIntrouvable() throws Exception {
    String csv =
        "Nom,Prenom,Email,Departement,DateEmbauche,TypeContrat\n"
            + "Alami,Sara,sara.import@test.ma,DepartementInexistant,10/03/2023,CDI\n";
    Map<String, Integer> mapping =
        Map.of(
            "nom", 0,
            "prenom", 1,
            "email", 2,
            "departementNom", 3,
            "dateEmbauche", 4,
            "typeContrat", 5);

    mockMvc
        .perform(
            multipart("/api/import/analyser")
                .file(fichierCsv(csv))
                .file(mappingJson(mapping))
                .param("cible", "EMPLOYES")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lignesErreur").value(1))
        .andExpect(jsonPath("$.data.lignes[0].statut").value("ERREUR"));
  }

  @Test
  void importeLeSoldeInitialEtLeCorrigeSansDupliquerLeMouvement() throws Exception {
    Departement dept = departementRepository.save(new Departement("Finance", null));
    Employe employe =
        employeRepository.save(
            new Employe(
                "Bennani",
                "Amine",
                "amine.solde@test.ma",
                null,
                null,
                dept,
                null,
                LocalDate.of(2022, 1, 1),
                TypeContratEmploye.CDI,
                null));

    String csv1 = "Email,Solde\namine.solde@test.ma,18\n";
    Map<String, Integer> mapping = Map.of("employeEmail", 0, "quantiteJours", 1);

    mockMvc
        .perform(
            multipart("/api/import/executer")
                .file(fichierCsv(csv1))
                .file(mappingJson(mapping))
                .param("cible", "SOLDES_CONGES_INITIAUX")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lignes[0].action").value("CREATION"));

    var mouvement =
        mouvementCongeRepository
            .findByEmployeIdAndTypeMouvement(employe.getId(), TypeMouvementConge.initialisation)
            .orElseThrow();
    assertThat(mouvement.getQuantiteJours()).isEqualByComparingTo("18");

    String csv2 = "Email,Solde\namine.solde@test.ma,22.5\n";
    mockMvc
        .perform(
            multipart("/api/import/executer")
                .file(fichierCsv(csv2))
                .file(mappingJson(mapping))
                .param("cible", "SOLDES_CONGES_INITIAUX")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lignes[0].action").value("MISE_A_JOUR"));

    assertThat(mouvementCongeRepository.count()).isEqualTo(1);
    var mouvementMisAJour =
        mouvementCongeRepository
            .findByEmployeIdAndTypeMouvement(employe.getId(), TypeMouvementConge.initialisation)
            .orElseThrow();
    assertThat(mouvementMisAJour.getQuantiteJours()).isEqualByComparingTo("22.5");
  }

  @Test
  void refuseUnMappingIncompletEtUnAccesManager() throws Exception {
    mockMvc
        .perform(
            multipart("/api/import/analyser")
                .file(fichierCsv("Nom\nX\n"))
                .file(mappingJson(Map.of()))
                .param("cible", "DEPARTEMENTS")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            multipart("/api/import/analyser")
                .file(fichierCsv("Nom\nX\n"))
                .file(mappingJson(Map.of("nom", 0)))
                .param("cible", "DEPARTEMENTS")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void suggereLeMappingDesColonnesALaPrevisualisation() throws Exception {
    String csv = "Nom,Prénom,E-mail\nDupont,Jean,jean@test.ma\n";

    mockMvc
        .perform(
            multipart("/api/import/previsualiser")
                .file(fichierCsv(csv))
                .param("cible", "EMPLOYES")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalLignes").value(1))
        .andExpect(jsonPath("$.data.mappingSuggere.nom").value(0))
        .andExpect(jsonPath("$.data.mappingSuggere.prenom").value(1))
        .andExpect(jsonPath("$.data.mappingSuggere.email").value(2));
  }
}
