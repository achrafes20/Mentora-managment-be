package ma.hbdev.rh.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Parsing tolérant des valeurs texte issues du tableur — formats les plus courants côté export
 * Excel français ; le fichier réel de la RH pourra en révéler d'autres (cf. porte de phase 2).
 */
final class ImportParseUtils {

  private static final List<DateTimeFormatter> FORMATS_DATE =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("dd/MM/yyyy"),
          DateTimeFormatter.ofPattern("dd-MM-yyyy"),
          DateTimeFormatter.ofPattern("d/M/yyyy"),
          DateTimeFormatter.ofPattern("dd.MM.yyyy"));

  private static final Map<String, TypeContratEmploye> SYNONYMES_CONTRAT =
      Map.ofEntries(
          Map.entry("cdi", TypeContratEmploye.CDI),
          Map.entry("cdd", TypeContratEmploye.CDD),
          Map.entry("stagiaire", TypeContratEmploye.STAGIAIRE),
          Map.entry("stage", TypeContratEmploye.STAGIAIRE),
          Map.entry("stagiaire remunere", TypeContratEmploye.STAGIAIRE_REMUNERE),
          Map.entry("stage remunere", TypeContratEmploye.STAGIAIRE_REMUNERE));

  private ImportParseUtils() {}

  static LocalDate parserDate(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    for (DateTimeFormatter format : FORMATS_DATE) {
      try {
        return LocalDate.parse(valeur.trim(), format);
      } catch (DateTimeParseException ignored) {
        // essai du format suivant
      }
    }
    return null;
  }

  static BigDecimal parserNombre(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(valeur.trim().replace(',', '.'));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static TypeContratEmploye parserTypeContrat(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    return SYNONYMES_CONTRAT.get(ColonneMatcher.normaliser(valeur));
  }
}
