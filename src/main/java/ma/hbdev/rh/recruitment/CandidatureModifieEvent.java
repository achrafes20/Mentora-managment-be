package ma.hbdev.rh.recruitment;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque ingestion/changement de statut/relance d'analyse — consommé par l'écouteur
 * d'audit (T3.A1).
 */
public record CandidatureModifieEvent(UUID candidatureId, String action)
    implements EvenementMetier {
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
}
