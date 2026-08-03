package ma.hbdev.rh.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

/** T4.B2 (re-scope) — EF-CFG-01/02 : identité de l'entreprise, table à une seule ligne logique. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class IdentiteEntrepriseIntegrationTest {

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
  void nettoyerEtAuthentifier() throws Exception {
    jdbcTemplate.execute(
        "TRUNCATE TABLE notifications_mattermost, notifications_in_app, journal_audit,"
            + " identite_entreprise");
    // fichiers.televerse_par référence utilisateurs(id) sans ON DELETE, et fichiers est lui-même
    // référencé par d'autres tables (employes...) — TRUNCATE le casserait sans lister toutes ces
    // tables. On ne nettoie que la référence dont ce test a besoin (upload de logo par un compte
    // qui va être recréé), sans toucher au reste.
    jdbcTemplate.update("UPDATE fichiers SET televerse_par = NULL");
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

  @Test
  void retourneUneReponseVideTantQueRienNaEteSaisi() throws Exception {
    mockMvc
        .perform(
            get("/api/config/identite-entreprise").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").doesNotExist())
        .andExpect(jsonPath("$.data.raisonSociale").doesNotExist());
  }

  @Test
  void modifieEtRelitLIdentite() throws Exception {
    String corps =
        "{\"raisonSociale\": \"HB Développement\", \"adresse\": \"Casablanca\","
            + " \"telephone\": \"+212600000000\", \"email\": \"contact@hbdev.ma\"}";

    mockMvc
        .perform(
            put("/api/config/identite-entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.raisonSociale").value("HB Développement"))
        .andExpect(jsonPath("$.data.modifiePar").isNotEmpty());

    mockMvc
        .perform(
            get("/api/config/identite-entreprise").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.adresse").value("Casablanca"))
        .andExpect(jsonPath("$.data.email").value("contact@hbdev.ma"));
  }

  @Test
  void modifieEtRelitIceRcEtVille() throws Exception {
    String corps =
        "{\"raisonSociale\": \"HB Développement\", \"ice\": \"001234567000089\","
            + " \"rc\": \"12345\", \"ville\": \"Tétouan\"}";

    mockMvc
        .perform(
            put("/api/config/identite-entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ice").value("001234567000089"))
        .andExpect(jsonPath("$.data.rc").value("12345"))
        .andExpect(jsonPath("$.data.ville").value("Tétouan"));
  }

  @Test
  void modifieEtRelitLeSignataire() throws Exception {
    String corps =
        "{\"raisonSociale\": \"HB Développement\", \"signataireNom\": \"Amal Medah\","
            + " \"signataireFonction\": \"Responsable RH\", \"signataireSexe\": \"FEMME\"}";

    mockMvc
        .perform(
            put("/api/config/identite-entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signataireNom").value("Amal Medah"))
        .andExpect(jsonPath("$.data.signataireFonction").value("Responsable RH"))
        .andExpect(jsonPath("$.data.signataireSexe").value("FEMME"));
  }

  @Test
  void neCreeJamaisUneDeuxiemeLigne() throws Exception {
    String premiere = "{\"raisonSociale\": \"Premier nom\"}";
    String seconde = "{\"raisonSociale\": \"Nom corrige\"}";

    mockMvc.perform(
        put("/api/config/identite-entreprise")
            .contentType(MediaType.APPLICATION_JSON)
            .content(premiere)
            .header("Authorization", "Bearer " + adminToken));
    mockMvc
        .perform(
            put("/api/config/identite-entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seconde)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(jsonPath("$.data.raisonSociale").value("Nom corrige"));

    Integer nombreLignes =
        jdbcTemplate.queryForObject("select count(*) from identite_entreprise", Integer.class);
    assertThat(nombreLignes).isEqualTo(1);
  }

  @Test
  void refuseAuManagerEnLectureEtEnEcriture() throws Exception {
    mockMvc
        .perform(
            get("/api/config/identite-entreprise")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/config/identite-entreprise")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"raisonSociale\": \"Peu importe\"}")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void televerseEtRecupereLeLogo() throws Exception {
    MockMultipartFile logo =
        new MockMultipartFile(
            "logo",
            "logo.png",
            "image/png",
            "contenu-image-factice".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/config/identite-entreprise/logo")
                .file(logo)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.logoFichierId").isNotEmpty());

    mockMvc
        .perform(
            get("/api/config/identite-entreprise/logo")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(content().bytes("contenu-image-factice".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void refuseLeTelechargementDuLogoQuandAucunNaEteTeleverse() throws Exception {
    mockMvc
        .perform(
            get("/api/config/identite-entreprise/logo")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void televerseEtRecupereLaSignature() throws Exception {
    MockMultipartFile signature =
        new MockMultipartFile(
            "signature",
            "signature.png",
            "image/png",
            "contenu-signature-factice".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/config/identite-entreprise/signature")
                .file(signature)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signatureFichierId").isNotEmpty());

    mockMvc
        .perform(
            get("/api/config/identite-entreprise/signature")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(content().bytes("contenu-signature-factice".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void refuseLeTelechargementDeLaSignatureQuandAucuneNaEteTeleversee() throws Exception {
    mockMvc
        .perform(
            get("/api/config/identite-entreprise/signature")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void refuseLeTeleversementDeSignatureAuManager() throws Exception {
    MockMultipartFile signature =
        new MockMultipartFile(
            "signature",
            "signature.png",
            "image/png",
            "peu-importe".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/config/identite-entreprise/signature")
                .file(signature)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void journaliseLaModificationEtLaRendConsultableViaAudit() throws Exception {
    mockMvc.perform(
        put("/api/config/identite-entreprise")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"raisonSociale\": \"HB Développement\"}")
            .header("Authorization", "Bearer " + adminToken));

    mockMvc
        .perform(
            get("/api/audit")
                .param("module", "configuration")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(
            jsonPath("$.data.content[0].action").value("config.identite_entreprise.modifiee"));
  }
}
