package ma.hbdev.rh.employee;

import jakarta.persistence.criteria.JoinType;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.jpa.domain.Specification;

/**
 * EF-EMP-04 / EF-EMP-12 — filtres combinables + recherche texte libre (nom, prénom, e-mail, id).
 */
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
              (root, query, cb) -> {
                // id est de type uuid côté Postgres : lower()/LIKE exigent un CAST explicite en
                // texte, contrairement à nom/prenom/email qui sont déjà varchar. cb.cast() est une
                // extension Hibernate (HibernateCriteriaBuilder), absente de jakarta.persistence's
                // CriteriaBuilder standard.
                HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
                @SuppressWarnings("unchecked")
                JpaExpression<UUID> idExpr = (JpaExpression<UUID>) (Object) root.get("id");
                return cb.or(
                    cb.like(cb.lower(root.get("nom")), motif),
                    cb.like(cb.lower(root.get("prenom")), motif),
                    cb.like(cb.lower(cb.coalesce(root.get("email"), "")), motif),
                    cb.like(cb.lower(hcb.cast(idExpr, String.class)), motif));
              });
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
