package ma.hbdev.rh.employee;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque création/modification/désactivation — consommé plus tard par l'écouteur d'audit
 * (T3.A1).
 */
public record DepartementModifieEvent(UUID departementId, String action)
    implements EvenementMetier {
  @Override
  public ModuleAudit module() {
    return ModuleAudit.employe;
  }

  @Override
  public String entiteType() {
    return "departement";
  }

  @Override
  public UUID entiteId() {
    return departementId;
  }
}
