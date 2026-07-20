package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record MouvementCongeReponse(
    UUID id,
    UUID employeId,
    UUID demandeId,
    TypeMouvementCongeAdm typeMouvement,
    BigDecimal quantiteJours,
    LocalDate dateMouvement,
    String commentaire,
    UUID creePar,
    Instant creeLe) {
  static MouvementCongeReponse depuis(MouvementCongeAdm mouvement) {
    return new MouvementCongeReponse(
        mouvement.getId(),
        mouvement.getEmployeId(),
        mouvement.getDemandeId(),
        mouvement.getTypeMouvement(),
        mouvement.getQuantiteJours(),
        mouvement.getDateMouvement(),
        mouvement.getCommentaire(),
        mouvement.getCreePar(),
        mouvement.getCreeLe());
  }
}
