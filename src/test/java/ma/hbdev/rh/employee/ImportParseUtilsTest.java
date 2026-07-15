package ma.hbdev.rh.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ImportParseUtilsTest {

  @Test
  void parseLesFormatsDeDateLesPlusCourants() {
    assertThat(ImportParseUtils.parserDate("2024-01-15")).isEqualTo(LocalDate.of(2024, 1, 15));
    assertThat(ImportParseUtils.parserDate("15/01/2024")).isEqualTo(LocalDate.of(2024, 1, 15));
    assertThat(ImportParseUtils.parserDate("15-01-2024")).isEqualTo(LocalDate.of(2024, 1, 15));
    assertThat(ImportParseUtils.parserDate("5/1/2024")).isEqualTo(LocalDate.of(2024, 1, 5));
    assertThat(ImportParseUtils.parserDate("")).isNull();
    assertThat(ImportParseUtils.parserDate(null)).isNull();
    assertThat(ImportParseUtils.parserDate("pas une date")).isNull();
  }

  @Test
  void parseLesNombresAvecVirguleOuPoint() {
    assertThat(ImportParseUtils.parserNombre("22")).isEqualByComparingTo(new BigDecimal("22"));
    assertThat(ImportParseUtils.parserNombre("22,5")).isEqualByComparingTo(new BigDecimal("22.5"));
    assertThat(ImportParseUtils.parserNombre("22.5")).isEqualByComparingTo(new BigDecimal("22.5"));
    assertThat(ImportParseUtils.parserNombre("abc")).isNull();
    assertThat(ImportParseUtils.parserNombre("")).isNull();
  }

  @Test
  void reconnaitLesSynonymesDeTypeDeContrat() {
    assertThat(ImportParseUtils.parserTypeContrat("CDI")).isEqualTo(TypeContratEmploye.CDI);
    assertThat(ImportParseUtils.parserTypeContrat("cdd")).isEqualTo(TypeContratEmploye.CDD);
    assertThat(ImportParseUtils.parserTypeContrat("Stagiaire"))
        .isEqualTo(TypeContratEmploye.STAGIAIRE);
    assertThat(ImportParseUtils.parserTypeContrat("Stagiaire rémunéré"))
        .isEqualTo(TypeContratEmploye.STAGIAIRE_REMUNERE);
    assertThat(ImportParseUtils.parserTypeContrat("Freelance")).isNull();
  }
}
