package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTO de réponse pour une anomalie de pointage. */
public record AnomaliePointageReponse(
    UUID id,
    UUID employeId,
    LocalDate datePointage,
    TypeAnomaliePointage typeAnomalie,
    boolean resolue,
    Instant creeLe) {

  static AnomaliePointageReponse depuis(AnomaliePointage a) {
    return new AnomaliePointageReponse(
        a.getId(),
        a.getEmployeId(),
        a.getDatePointage(),
        a.getTypeAnomalie(),
        a.isResolue(),
        a.getCreeLe());
  }
}
