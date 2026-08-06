package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** EF-ATT-04/05 — filtres combinables pour la liste des anomalies (employé, type, période). */
final class AnomalieSpecifications {

  private AnomalieSpecifications() {}

  /**
   * EF-AUTH-03 : {@code employeIds} restreint aux employés du périmètre du Manager courant, {@code
   * null} = pas de restriction (Admin). Voir {@link PointageSpecifications#filtrer} pour le même
   * principe côté pointages.
   */
  static Specification<AnomaliePointage> filtrer(
      List<UUID> employeIds,
      TypeAnomaliePointage type,
      Boolean resolue,
      LocalDate debut,
      LocalDate fin) {
    Specification<AnomaliePointage> spec = (root, query, cb) -> cb.conjunction();
    if (employeIds != null) {
      spec = spec.and((root, query, cb) -> root.get("employeId").in(employeIds));
    }
    if (type != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("typeAnomalie"), type));
    }
    if (resolue != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("resolue"), resolue));
    }
    if (debut != null) {
      spec =
          spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("datePointage"), debut));
    }
    if (fin != null) {
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("datePointage"), fin));
    }
    return spec;
  }
}
