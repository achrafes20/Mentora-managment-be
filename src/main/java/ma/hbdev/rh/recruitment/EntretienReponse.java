package ma.hbdev.rh.recruitment;

import java.time.Instant;
import java.util.UUID;

public record EntretienReponse(
    UUID id,
    UUID candidatureId,
    UUID managerId,
    String resultat,
    String commentaire,
    Instant dateEntretien,
    Instant dateResultat,
    Instant creeLe) {

  static EntretienReponse depuis(Entretien entretien) {
    return new EntretienReponse(
        entretien.getId(),
        entretien.getCandidatureId(),
        entretien.getManagerId(),
        entretien.getResultat() != null ? entretien.getResultat().name() : null,
        entretien.getCommentaire(),
        entretien.getDateEntretien(),
        entretien.getDateResultat(),
        entretien.getCreeLe());
  }
}
