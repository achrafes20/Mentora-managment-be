package ma.hbdev.rh.employee;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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

  private UUID departementId;
  private UUID autreDepartementId;

  @BeforeEach
  void creerDepartements() {
    departementId =
        departementRepository
            .save(new Departement("Ingenierie " + UUID.randomUUID(), null))
            .getId();
    autreDepartementId =
        departementRepository.save(new Departement("Ventes " + UUID.randomUUID(), null)).getId();
  }

  private String requeteCreation(String email, String typeContrat, String dateFinContratPrevue)
      throws Exception {
    return """
        {"nom":"Dupont","prenom":"Jean","email":"%s","telephone":"0600000000","poste":"Dev",
         "departementId":"%s","dateEmbauche":"2024-01-15","typeContrat":"%s",
         "dateFinContratPrevue":%s}
        """
        .formatted(
            email,
            departementId,
            typeContrat,
            dateFinContratPrevue == null ? "null" : "\"" + dateFinContratPrevue + "\"");
  }

  @Test
  void creeListeModifieEtTransfereUnEmploye() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
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
        .perform(get("/api/employes").param("recherche", "Dupont"))
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteTransfert))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.departementId").value(autreDepartementId.toString()));

    mockMvc
        .perform(get("/api/employes/{id}/transferts", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].nouveauDepartementId").value(autreDepartementId.toString()));
  }

  @Test
  void refuseUnEmailDejaUtilise() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("dup@test.ma", "CDI", null)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/employes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("dup@test.ma", "CDI", null)))
        .andExpect(status().isConflict());
  }

  @Test
  void accepteDateFinContratSurCddEtLaRefuseAilleurs() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("cdd@test.ma", "CDD", "2024-12-31")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dateFinContratPrevue").value("2024-12-31"));

    mockMvc
        .perform(
            post("/api/employes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("cdi-avec-date@test.ma", "CDI", "2024-12-31")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void desactiveUnEmployeAvecMotifEtDateDepart() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation("depart@test.ma", "CDI", null)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(
            post("/api/employes/{id}/desactiver", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motif":"demission","dateDepart":"2024-07-01"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/employes/{id}", id))
        .andExpect(jsonPath("$.data.statut").value("inactif"))
        .andExpect(jsonPath("$.data.motifDepart").value("demission"));
  }

  @Test
  void bloqueLaDesactivationDuDepartementTantQuUnEmployeActifYEstRattache() throws Exception {
    mockMvc
        .perform(
            post("/api/employes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCreation("actif@test.ma", "CDI", null)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/departements/{id}", departementId))
        .andExpect(status().isConflict());
  }

  @Test
  void attacheEtListeUnDocument() throws Exception {
    String reponseCreation =
        mockMvc
            .perform(
                post("/api/employes")
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
            .perform(multipart("/api/employes/{id}/documents", id).file(fichier).file(typeDocument))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nomOriginal").value("contrat.pdf"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = objectMapper.readTree(reponseDocument).at("/data/id").asText();

    mockMvc
        .perform(get("/api/employes/{id}/documents", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].typeDocument").value("contrat"));

    mockMvc
        .perform(get("/api/employes/{id}/documents/{documentId}/telecharger", id, documentId))
        .andExpect(status().isOk())
        .andExpect(content().bytes("contenu-test".getBytes()))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("contrat.pdf")));
  }
}
