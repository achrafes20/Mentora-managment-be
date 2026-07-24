package ma.hbdev.rh.administrative;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * EF-ADM-12 : journalisé à la création/suppression — jamais délégable (gestion Admin uniquement).
 */
record PeriodeBlocageCongesEvent(UUID periodeId, String action) implements EvenementMetier {

  @Override
  public ModuleAudit module() {
    return ModuleAudit.demande_administrative;
  }

  @Override
  public String entiteType() {
    return "periode_blocage_conges";
  }

  @Override
  public UUID entiteId() {
    return periodeId;
  }
}
