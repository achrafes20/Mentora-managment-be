package ma.hbdev.rh.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Couche HTTP du module Document (T4.A1), jusqu'ici jamais exercée par un test d'intégration —
 * {@code DocumentRhControllerTest} (malgré son nom) mocke {@link DocumentRhService} en dessous de
 * la couche HTTP, et {@link SurveillanceController} n'était référencé par aucun test.
 *
 * <p>{@link DocumentRhService#envoyerCertificatStage} (et les deux autres méthodes d'envoi)
 * appellent réellement le webhook n8n et propagent l'échec (contrairement au motif
 * "fire-and-forget" de {@link SurveillancePlanifieeService}) : un stub HTTP local (JDK {@link
 * HttpServer}, même principe que {@code MattermostApiClientTest}) tient lieu de n8n pour permettre
 * de tester le chemin nominal, pas seulement les échecs RBAC.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DocumentRhIntegrationTest {

  private static final String SECRET_WEBHOOK = "test-secret-integration";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  // Champ statique à initialisation immédiate : garanti démarré avant que Spring ne prépare le
  // contexte (donc avant l'appel de n8nBaseUrl ci-dessous), sans dépendre de l'ordre relatif
  // entre @DynamicPropertySource et un éventuel @BeforeAll.
  private static final HttpServer N8N_STUB = demarrerStubN8n();
  private static final AtomicInteger appelsWebhook = new AtomicInteger();

  private static HttpServer demarrerStubN8n() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext(
          "/webhook/notify-email",
          exchange -> {
            appelsWebhook.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Impossible de démarrer le stub n8n de test", e);
    }
  }

  @DynamicPropertySource
  static void n8nBaseUrl(DynamicPropertyRegistry registry) {
    registry.add("app.n8n.base-url", () -> "http://localhost:" + N8N_STUB.getAddress().getPort());
    registry.add("app.security.internal-webhook-secret", () -> SECRET_WEBHOOK);
  }

  // HttpServer utilise des threads non-daemon par défaut : sans arrêt explicite, la JVM du fork
  // Surefire ne se termine pas d'elle-même (même besoin que MattermostApiClientTest, ici en
  // @AfterAll car le serveur est un champ statique partagé par toute la classe).
  @AfterAll
  static void arreterStubN8n() {
    N8N_STUB.stop(0);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private NotificationPlanifieeRepository notificationPlanifieeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String managerToken;

  @BeforeEach
  void authentifierAdmin() throws Exception {
    appelsWebhook.set(0);
    jdbcTemplate.execute(
        """
        truncate table notifications_mattermost, notifications_in_app, journal_audit,
          envois_documents, notifications_planifiees, employe_documents, fichiers,
          employes, departements, sessions_utilisateur
        cascade
        """);
    jdbcTemplate.execute("DELETE FROM utilisateurs");

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
    manager.setNom("Alaoui");
    manager.setPrenom("Sara");
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

  private UUID creerEmploye(String nom, String email) throws Exception {
    String reqDept = "{\"nom\":\"RH %s\",\"managerId\":null}".formatted(nom);
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    String emailJson = email == null ? "null" : "\"" + email + "\"";
    String reqEmp =
        """
        {"nom":"%s","prenom":"Employe","email":%s,"poste":"Dev","departementId":"%s",
         "dateEmbauche":"2025-01-01","typeContrat":"CDI"}
        """
            .formatted(nom, emailJson, deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());
  }

  // Toute la couche est @PreAuthorize("hasRole('ADMIN')") au niveau du contrôleur. Anonyme -> 401
  // (ApiAuthenticationEntryPoint, SecurityConfig) ; authentifié mais rôle insuffisant -> 403
  // (AccessDeniedException, intercepté par GlobalExceptionHandler dans les deux cas ci-dessous).
  @Test
  void refuseSansAuthentificationEtAuManager() throws Exception {
    UUID employeId = creerEmploye("Rbac", "rbac@hbdev.ma");

    mockMvc
        .perform(get("/api/documents/employes/{id}", employeId))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/documents/employes/{id}", employeId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/api/documents/employes/{id}/certificat-travail", employeId))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/certificat-travail", employeId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // 1, pas 0 : creerEmploye() envoie un e-mail (code de pointage kiosque, EF-ATT-18) dès la
    // création — tous les appels ci-dessus sont rejetés en RBAC avant d'atteindre le webhook.
    assertThat(appelsWebhook.get()).isEqualTo(1);
  }

  @Test
  void envoieDesCertificatsEtUnDocumentLibreEtLesRetrouveDansLHistorique() throws Exception {
    UUID employeId = creerEmploye("Certificats", "certificats@hbdev.ma");

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/certificat-travail", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.typeDocument").value("certificat_travail"))
        .andExpect(jsonPath("$.data.destinataireEmail").value("certificats@hbdev.ma"));

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/certificat-stage", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.typeDocument").value("certificat_stage"));

    MockMultipartFile fichier =
        new MockMultipartFile("file", "libre.pdf", "application/pdf", "contenu-test".getBytes());
    mockMvc
        .perform(
            multipart("/api/documents/employes/{id}/document-libre", employeId)
                .file(fichier)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.typeDocument").value("document_libre"));

    mockMvc
        .perform(
            get("/api/documents/employes/{id}", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3));

    // 4, pas 3 : les trois envois de document + l'e-mail de code kiosque envoyé automatiquement
    // à la création de l'employé (EF-ATT-18) sont tous bien passés par le (stub) webhook n8n.
    assertThat(appelsWebhook.get()).isEqualTo(4);
  }

  @Test
  void envoieUneAttestationDeTravailPourUnEmployeActifCdi() throws Exception {
    UUID employeId = creerEmploye("Attestation", "attestation@hbdev.ma");

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/attestation-travail", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.typeDocument").value("attestation_travail"))
        .andExpect(jsonPath("$.data.destinataireEmail").value("attestation@hbdev.ma"));

    // 2 : e-mail de code kiosque à la création (EF-ATT-18) + l'envoi de l'attestation.
    assertThat(appelsWebhook.get()).isEqualTo(2);
  }

  @Test
  void refuseLAttestationDeTravailPourUnEmployeInactif() throws Exception {
    UUID employeId = creerEmploye("Inactif", "inactif@hbdev.ma");
    mockMvc
        .perform(
            post("/api/employes/{id}/desactiver", employeId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"demission\",\"dateDepart\":\"2026-01-01\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/attestation-travail", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error")
                .value("L'attestation de travail n'est disponible que pour un employé actif."));

    // 1, pas 0 : e-mail de code kiosque envoyé à la création (EF-ATT-18) ; la désactivation et le
    // refus d'attestation ci-dessus ne déclenchent aucun envoi supplémentaire.
    assertThat(appelsWebhook.get()).isEqualTo(1);
  }

  @Test
  void refuseLAttestationDeTravailPourUnStagiaire() throws Exception {
    String reqDept = "{\"nom\":\"RH Stagiaire\",\"managerId\":null}";
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    String reqEmp =
        """
        {"nom":"Stagiaire","prenom":"Employe","email":"stagiaire@hbdev.ma","poste":"Dev",
         "departementId":"%s","dateEmbauche":"2025-01-01","typeContrat":"STAGIAIRE",
         "dateFinStagePrevue":"2099-06-30"}
        """
            .formatted(deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/attestation-travail", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error")
                .value(
                    "L'attestation de travail n'est disponible que pour un employé CDI ou CDD."));

    // 1, pas 0 : e-mail de code kiosque à la création (EF-ATT-18), tous types de contrat confondus
    // — y compris Stagiaire, cf. KiosqueActivationService#gererEvenementEmploye.
    assertThat(appelsWebhook.get()).isEqualTo(1);
  }

  @Test
  void refuseUnCertificatSiLEmployeNAPasDEmail() throws Exception {
    UUID employeId = creerEmploye("SansEmail", null);

    mockMvc
        .perform(
            post("/api/documents/employes/{id}/certificat-travail", employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error")
                .value("L'adresse e-mail de l'employé est requise pour envoyer ce document."));

    assertThat(appelsWebhook.get()).isZero();
  }

  // Bouton "Forcer exécution Cron" (écran Documents) : authentifié Bearer + RBAC normal, jamais
  // le secret partagé InternalWebhookGuard (réservé au cron n8n, cf. /api/internal/surveillance).
  @Test
  void executeLeBalayageDeSurveillancePourUnAdminEtLeRefuseAuManager() throws Exception {
    UUID employeId = creerEmploye("Manuel", "manuel@hbdev.ma");
    NotificationPlanifiee notif =
        notificationPlanifieeRepository.save(
            new NotificationPlanifiee(employeId, TypeFinSurveillee.fin_cdd, LocalDate.now()));

    mockMvc
        .perform(post("/api/documents/surveillance/executer"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/documents/surveillance/executer")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/documents/surveillance/executer")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    String statutApres =
        jdbcTemplate.queryForObject(
            "select statut from notifications_planifiees where id = ?",
            String.class,
            notif.getId());
    assertThat(statutApres).isEqualTo("envoyee");
    // 2 : e-mail de code kiosque à la création (EF-ATT-18) + l'alerte de surveillance envoyée.
    assertThat(appelsWebhook.get()).isEqualTo(2);
  }

  @Test
  void listeEtRenvoieDepuisLaFileDeSurveillance() throws Exception {
    UUID employeId = creerEmploye("Surveillance", "surveillance@hbdev.ma");
    NotificationPlanifiee notif =
        notificationPlanifieeRepository.save(
            new NotificationPlanifiee(employeId, TypeFinSurveillee.fin_cdd, LocalDate.now()));

    mockMvc
        .perform(get("/api/documents/surveillance").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(notif.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].statut").value("planifiee"));

    mockMvc
        .perform(
            post("/api/documents/surveillance/{id}/renvoyer", notif.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.typeDocument").value("certificat_travail"));

    String statutApres =
        jdbcTemplate.queryForObject(
            "select statut from notifications_planifiees where id = ?",
            String.class,
            notif.getId());
    assertThat(statutApres).isEqualTo("envoyee");
    // 2 : e-mail de code kiosque à la création (EF-ATT-18) + le renvoi du certificat.
    assertThat(appelsWebhook.get()).isEqualTo(2);
  }

  // Même garde que RecruitmentIngestionController (InternalWebhookGuard) — jamais exercée ici
  // avant ce test, alors que c'est celle qui protège un endpoint appelé par un cron n8n public.
  @Test
  void guardeLEndpointInterneDeSurveillanceParLeSecretPartageEtTraiteLesNotificationsDues()
      throws Exception {
    UUID employeId = creerEmploye("Cron", "cron@hbdev.ma");
    NotificationPlanifiee notif =
        notificationPlanifieeRepository.save(
            new NotificationPlanifiee(employeId, TypeFinSurveillee.fin_cdd, LocalDate.now()));

    mockMvc.perform(post("/api/internal/surveillance/run")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(post("/api/internal/surveillance/run").header("X-Internal-Webhook-Secret", "faux"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/internal/surveillance/run")
                .header("X-Internal-Webhook-Secret", SECRET_WEBHOOK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"));

    String statutApres =
        jdbcTemplate.queryForObject(
            "select statut from notifications_planifiees where id = ?",
            String.class,
            notif.getId());
    assertThat(statutApres).isEqualTo("envoyee");
    // 2 : e-mail de code kiosque à la création (EF-ATT-18) + l'alerte de surveillance envoyée.
    assertThat(appelsWebhook.get()).isEqualTo(2);
  }
}
