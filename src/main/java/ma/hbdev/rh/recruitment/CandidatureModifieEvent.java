package ma.hbdev.rh.recruitment;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque ingestion/changement de statut/relance d'analyse — consommé par l'écouteur
 * d'audit (T3.A1). {@code candidatNomComplet} n'est là que pour {@link #details()} — rendre l'audit
 * cherchable par nom de candidat (EF-CFG-04).
 */
public record CandidatureModifieEvent(UUID candidatureId, String action, String candidatNomComplet)
    implements EvenementMetier {

  public CandidatureModifieEvent(UUID candidatureId, String action) {
    this(candidatureId, action, null);
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.recrutement;
  }

  @Override
  public String entiteType() {
    return "candidature";
  }

  @Override
  public UUID entiteId() {
    return candidatureId;
  }

  /** EF-AUTH-14 : la décision de recrutement, pas les statuts intermédiaires du pipeline. */
  @Override
  public boolean decisionDelegable() {
    return "statut_embauche".equals(action) || "statut_rejete".equals(action);
  }

  /**
   * Exclues de journal_audit : "reception_dupliquee" et "ingestion" ne sont pas des décisions
   * (intake automatique, un e-mail qui arrive) ; "statut_decision_auto" est l'avance automatique du
   * pipeline après un résultat d'entretien (jamais une transition manuelle équivalente côté Admin —
   * cf. CandidatureService) ; "entretien_reprogramme" est un ajustement de planning, pas une
   * décision d'embauche/rejet. Tout le reste (statuts de décision, relance d'analyse, réactivation,
   * archivage) reste audité.
   */
  @Override
  public boolean audite() {
    return !"reception_dupliquee".equals(action)
        && !"ingestion".equals(action)
        && !"statut_decision_auto".equals(action)
        && !"entretien_reprogramme".equals(action);
  }

  @Override
  public Map<String, Object> details() {
    return candidatNomComplet == null ? Map.of() : Map.of("candidat", candidatNomComplet);
  }
}
