package ma.hbdev.rh.employee;

import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** EF-EMP-04 / EF-EMP-12 — filtres combinables + recherche texte libre (nom, prénom, e-mail). */
final class EmployeSpecifications {

  private EmployeSpecifications() {}

  static Specification<Employe> filtrer(
      UUID departementId,
      UUID managerId,
      TypeContratEmploye typeContrat,
      StatutActifInactif statut,
      String recherche) {
    Specification<Employe> spec = fetchDepartement();
    if (departementId != null) {
      spec =
          spec.and((root, query, cb) -> cb.equal(root.get("departement").get("id"), departementId));
    }
    if (managerId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("managerId"), managerId));
    }
    if (typeContrat != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("typeContrat"), typeContrat));
    }
    if (statut != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
    }
    if (recherche != null) {
      String motif = "%" + recherche.toLowerCase() + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("nom")), motif),
                      cb.like(cb.lower(root.get("prenom")), motif),
                      cb.like(cb.lower(cb.coalesce(root.get("email"), "")), motif)));
    }
    return spec;
  }

  private static Specification<Employe> fetchDepartement() {
    return (root, query, cb) -> {
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch("departement", JoinType.LEFT);
      }
      return cb.conjunction();
    };
  }
}
