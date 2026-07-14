package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

/** DTO de réponse pour un pointage. */
public record PointageReponse(
    UUID id,
    UUID employeId,
    TypeScanPointage typeScan,
    Instant horodatage,
    boolean corrigeManuellement,
    String motifCorrection) {

  static PointageReponse depuis(Pointage p) {
    return new PointageReponse(
        p.getId(),
        p.getEmployeId(),
        p.getTypeScan(),
        p.getHorodatage(),
        p.isCorrigeManuellement(),
        p.getMotifCorrection());
  }
}
