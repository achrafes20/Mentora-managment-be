package ma.hbdev.rh;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test : vérifie que le contexte Spring se charge sans erreur.
 *
 * <p>Ce test n'utilise pas Testcontainers — la base de données est fournie par l'environnement de
 * CI ou par docker-compose (profil "test" utilise H2 in-memory pour ce test uniquement). Le test
 * Testcontainers + Flyway est dans {@code MigrationIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class RhApplicationTests {

  @Test
  void contextLoads() {
    // Le simple chargement du contexte sans exception suffit à valider le smoke test.
  }
}
