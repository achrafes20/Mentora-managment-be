package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** EF-ATT-05 — filtres combinables pour l'historique des pointages (employé, type, période). */
final class PointageSpecifications {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

  private PointageSpecifications() {}

  static Specification<Pointage> filtrer(
      UUID employeId, TypeScanPointage typeScan, LocalDate debut, LocalDate fin) {
    Specification<Pointage> spec = (root, query, cb) -> cb.conjunction();
    if (employeId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("employeId"), employeId));
    }
    if (typeScan != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("typeScan"), typeScan));
    }
    if (debut != null) {
      Instant debutInstant = debut.atStartOfDay(ZONE).toInstant();
      spec =
          spec.and(
              (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("horodatage"), debutInstant));
    }
    if (fin != null) {
      Instant finInstant = fin.plusDays(1).atStartOfDay(ZONE).toInstant();
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("horodatage"), finInstant));
    }
    return spec;
  }
}
