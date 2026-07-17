package ma.hbdev.rh.recruitment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyseIaReponse(
    UUID id,
    String statut,
    String extraitPrenom,
    String extraitNom,
    String extraitEmail,
    String extraitTelephone,
    String extraitIntitulePoste,
    BigDecimal scoreCorrespondance,
    BigDecimal anneesExperienceEstimees,
    String justificationScore,
    List<String> motsCles,
    Instant dateAnalyse) {

  static AnalyseIaReponse depuis(AnalyseIa analyse) {
    return new AnalyseIaReponse(
        analyse.getId(),
        analyse.getStatut().name(),
        analyse.getExtraitPrenom(),
        analyse.getExtraitNom(),
        analyse.getExtraitEmail(),
        analyse.getExtraitTelephone(),
        analyse.getExtraitIntitulePoste(),
        analyse.getScoreCorrespondance(),
        analyse.getAnneesExperienceEstimees(),
        analyse.getJustificationScore(),
        MotsClesUtils.versListe(analyse.getMotsCles()),
        analyse.getDateAnalyse());
  }
}
