package ma.hbdev.rh;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test d'intégration Flyway — vérifie que toutes les migrations s'appliquent sans erreur sur un
 * PostgreSQL 14 réel (NFR-TEST-01).
 *
 * <p>Utilise {@code @ServiceConnection} de Spring Boot Testcontainers pour injecter automatiquement
 * les propriétés de datasource sans configuration manuelle.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class MigrationIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private Flyway flyway;

  @Test
  void allMigrationsApplySuccessfully() {
    var all = flyway.info().all();
    // Toutes les migrations doivent être appliquées avec succès (aucun état FAILED)
    assertThat(Arrays.stream(all)).noneMatch(m -> m.getState() == MigrationState.FAILED);
  }

  @Test
  void migrationsCountMatchesExpected() {
    // Au moins V1 + V2 doivent être présentes
    assertThat(flyway.info().applied().length).isGreaterThanOrEqualTo(2);
  }
}
