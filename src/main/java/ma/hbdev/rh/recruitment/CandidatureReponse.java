package ma.hbdev.rh.recruitment;

import java.time.Instant;
import java.util.UUID;

public record CandidatureReponse(
    UUID id,
    UUID offreId,
    String nom,
    String prenom,
    String email,
    String telephone,
    String intitulePosteDetecte,
    String source,
    UUID cvFichierId,
    String statut,
    Instant dateIngestion,
    Instant dateArchivage,
    String messageCandidat,
    AnalyseIaReponse derniereAnalyse) {

  static CandidatureReponse depuis(Candidature candidature) {
    AnalyseIa analyse = candidature.getAnalyseCourante();
    return new CandidatureReponse(
        candidature.getId(),
        candidature.getOffreId(),
        candidature.getNom(),
        candidature.getPrenom(),
        candidature.getEmail(),
        candidature.getTelephone(),
        candidature.getIntitulePosteDetecte(),
        candidature.getSource().name(),
        candidature.getCvFichierId(),
        candidature.getStatut().name(),
        candidature.getDateIngestion(),
        candidature.getDateArchivage(),
        candidature.getMessageCandidat(),
        analyse != null ? AnalyseIaReponse.depuis(analyse) : null);
  }
}
