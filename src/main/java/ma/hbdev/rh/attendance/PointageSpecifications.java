package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** EF-ATT-05 — filtres combinables pour l'historique des pointages (employé, type, période). */
final class PointageSpecifications {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

  private PointageSpecifications() {}

  /**
   * EF-AUTH-03 : {@code employeIds} restreint aux employés du périmètre du Manager courant (liste
   * précalculée par {@code PointageService#employesDansPerimetre}, {@code null} = pas de
   * restriction, cas Admin). Remplace l'ancien paramètre {@code employeId} unique — un Manager y
   * voyait jusqu'ici les pointages de toute l'entreprise, contrairement à l'export qui filtrait
   * déjà par équipe.
   */
  static Specification<Pointage> filtrer(
      List<UUID> employeIds, TypeScanPointage typeScan, LocalDate debut, LocalDate fin) {
    Specification<Pointage> spec = (root, query, cb) -> cb.conjunction();
    if (employeIds != null) {
      spec = spec.and((root, query, cb) -> root.get("employeId").in(employeIds));
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
