package ma.hbdev.rh.recruitment;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque création/modification/fermeture d'offre — consommé par l'écouteur d'audit
 * (T3.A1), même principe que {@code DepartementModifieEvent} / {@code EmployeModifieEvent}.
 */
public record OffreEmploiModifieEvent(UUID offreId, String action) implements EvenementMetier {
  @Override
  public ModuleAudit module() {
    return ModuleAudit.recrutement;
  }

  @Override
  public String entiteType() {
    return "offre_emploi";
  }

  @Override
  public UUID entiteId() {
    return offreId;
  }
}
