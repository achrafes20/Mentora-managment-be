package ma.hbdev.rh.document;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CertificatGeneratorTest {

  private final CertificatGenerator generator = new CertificatGenerator();

  @Test
  void testGenererCertificatStage() {
    byte[] pdf =
        generator.genererCertificatStage(
            "Jean", "Dupont", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 30), "Developpeur");
    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
  }

  @Test
  void testGenererCertificatTravail() {
    byte[] pdf =
        generator.genererCertificatTravail(
            "Jean", "Dupont", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 12, 31), "Manager");
    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
  }
}
