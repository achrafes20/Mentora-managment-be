package ma.hbdev.rh.shared.export;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formatage FR d'un horodatage pour affichage dans un export (Excel/PDF) — évite qu'un {@code
 * Instant.toString()} brut (ex. "2026-07-24T13:42:54.361625Z") atterrisse devant un utilisateur RH.
 * Fuseau Africa/Casablanca, cohérent avec le reste de l'app.
 */
public final class FormatageExport {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
  private static final DateTimeFormatter FORMAT_DATE_HEURE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);

  private FormatageExport() {}

  public static String dateHeure(Instant instant) {
    return instant == null ? "" : FORMAT_DATE_HEURE.format(instant);
  }
}
