package ma.hbdev.rh.recruitment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OffreEmploiReponse(
    UUID id,
    String intitule,
    String description,
    UUID departementId,
    String statut,
    List<String> motsClesRequis,
    String categorie,
    UUID creePar,
    Instant creeLe,
    Instant fermeeLe) {

  static OffreEmploiReponse depuis(OffreEmploi offre) {
    return new OffreEmploiReponse(
        offre.getId(),
        offre.getIntitule(),
        offre.getDescription(),
        offre.getDepartementId(),
        offre.getStatut().name(),
        MotsClesUtils.versListe(offre.getMotsClesRequis()),
        offre.getCategorie(),
        offre.getCreePar(),
        offre.getCreeLe(),
        offre.getFermeeLe());
  }
}
