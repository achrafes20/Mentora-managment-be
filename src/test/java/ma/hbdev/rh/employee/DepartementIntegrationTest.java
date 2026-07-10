package ma.hbdev.rh.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Réplique le pattern de {@code MigrationIntegrationTest} : Testcontainers, PostgreSQL 14 réel. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DepartementIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void creeListeModifieEtDesactiveUnDepartement() throws Exception {
    String requeteCreation =
        objectMapper.writeValueAsString(new DepartementRequete("Ressources Humaines", null));

    String reponseCreation =
        mockMvc
            .perform(
                post("/api/departements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteCreation))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.nom").value("Ressources Humaines"))
            .andExpect(jsonPath("$.data.statut").value("actif"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(reponseCreation).at("/data/id").asText();

    mockMvc
        .perform(get("/api/departements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.nom == 'Ressources Humaines')]").exists());

    String requeteModification =
        objectMapper.writeValueAsString(new DepartementRequete("RH & Paie", null));
    mockMvc
        .perform(
            put("/api/departements/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteModification))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nom").value("RH & Paie"));

    mockMvc.perform(delete("/api/departements/{id}", id)).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/departements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.nom == 'RH & Paie')].statut").value("inactif"));

    mockMvc
        .perform(post("/api/departements/{id}/activer", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.statut").value("actif"));
  }

  @Test
  void refuseUnNomDeDepartementDejaUtilise() throws Exception {
    String requete = objectMapper.writeValueAsString(new DepartementRequete("Finance", null));

    mockMvc
        .perform(post("/api/departements").contentType(MediaType.APPLICATION_JSON).content(requete))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/departements").contentType(MediaType.APPLICATION_JSON).content(requete))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void renvoie404SurUnDepartementInconnu() throws Exception {
    String requete = objectMapper.writeValueAsString(new DepartementRequete("Peu importe", null));

    mockMvc
        .perform(
            put("/api/departements/{id}", "00000000-0000-0000-0000-000000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requete))
        .andExpect(status().isNotFound());
  }
}
