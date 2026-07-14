package ma.hbdev.rh.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AttendanceIntegrationTest {

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
  @Autowired private QrCodeRepository qrCodeRepository;
  @Autowired private PointageRepository pointageRepository;

  private String adminToken;

  @BeforeEach
  void authentifierAdmin() throws Exception {
    sessionRepository.deleteAll();
    userRepository.deleteAll();
    pointageRepository.deleteAll();
    qrCodeRepository.deleteAll();

    User admin = new User();
    admin.setEmail("admin@hbdev.ma");
    admin.setMotDePasseHash(passwordEncoder.encode("AdminPass@2025"));
    admin.setRole(RoleUtilisateur.admin);
    admin.setNom("System");
    admin.setPrenom("Admin");
    userRepository.save(admin);

    String requete = objectMapper.writeValueAsString(new LoginRequest("admin@hbdev.ma", "AdminPass@2025"));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requete))
            .andExpect(status().isOk())
            .andReturn();
    adminToken =
        objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
  }

  @Test
  void testQrCodeEtPointageKiosque() throws Exception {
    // 1. Créer un département
    String reqDept = "{\"nom\":\"RH Test\",\"managerId\":null}";
    String resDept = mockMvc.perform(post("/api/departements").header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON).content(reqDept))
        .andReturn().getResponse().getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    // 2. Créer un employé
    String reqEmp = """
        {
          "nom": "Test",
          "prenom": "Employe",
          "email": "test@hbdev.ma",
          "telephone": "0600000000",
          "poste": "Dev",
          "departementId": "%s",
          "dateEmbauche": "2025-01-01",
          "typeContrat": "CDI"
        }
        """.formatted(deptId);
    String resEmp = mockMvc.perform(post("/api/employes").header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON).content(reqEmp))
        .andReturn().getResponse().getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    // 3. Générer QR code
    String resQr =
        mockMvc
            .perform(
                post("/api/pointages/qr-code/generer/" + employeId)
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String valeurQr = objectMapper.readTree(resQr).at("/data/valeur").asText();

    // Scan kiosque entrée
    String scanReqEntree =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.entree));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.typeScan").value("entree"));

    // Double entrée -> refusée
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isConflict());

    // Scan kiosque sortie
    String scanReqSortie =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.sortie));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqSortie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.typeScan").value("sortie"));
  }
}
