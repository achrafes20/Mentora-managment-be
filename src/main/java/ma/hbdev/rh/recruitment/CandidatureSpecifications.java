package ma.hbdev.rh.recruitment;

import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** EF-REC-06 — filtres combinables (offre, statut, score) + recherche texte libre (nom, e-mail). */
final class CandidatureSpecifications {

  private CandidatureSpecifications() {}

  static Specification<Candidature> filtrer(
      UUID offreId, StatutCandidature statut, BigDecimal scoreMin, String recherche) {
    Specification<Candidature> spec = fetchAnalyseCourante();
    if (offreId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("offreId"), offreId));
    }
    if (statut != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
    }
    if (scoreMin != null) {
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.greaterThanOrEqualTo(
                      root.get("analyseCourante").get("scoreCorrespondance"), scoreMin));
    }
    if (recherche != null) {
      String motif = "%" + recherche.toLowerCase() + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(cb.coalesce(root.get("nom"), "")), motif),
                      cb.like(cb.lower(cb.coalesce(root.get("prenom"), "")), motif),
                      cb.like(cb.lower(root.get("email")), motif)));
    }
    return spec;
  }

  // EF-REC-08 : un Manager ne voit que les candidatures pour lesquelles un entretien lui a été
  // assigné (cf. CandidatureService.verifierPerimetreManager).
  static Specification<Candidature> pourManager(UUID managerId) {
    return (root, query, cb) -> {
      var sousRequete = query.subquery(UUID.class);
      var entretien = sousRequete.from(Entretien.class);
      sousRequete
          .select(entretien.get("candidatureId"))
          .where(cb.equal(entretien.get("managerId"), managerId));
      return root.get("id").in(sousRequete);
    };
  }

  private static Specification<Candidature> fetchAnalyseCourante() {
    return (root, query, cb) -> {
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch("analyseCourante", JoinType.LEFT);
      }
      return cb.conjunction();
    };
  }
}
