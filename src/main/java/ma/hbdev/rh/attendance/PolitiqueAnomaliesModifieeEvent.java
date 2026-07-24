package ma.hbdev.rh.attendance;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/** EF-ATT-11 : journalisé à chaque modification — jamais délégable (gestion Admin uniquement). */
record PolitiqueAnomaliesModifieeEvent(UUID politiqueId) implements EvenementMetier {

  @Override
  public String action() {
    return "politique_anomalies.modifiee";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.presence;
  }

  @Override
  public String entiteType() {
    return "politique_anomalies";
  }

  @Override
  public UUID entiteId() {
    return politiqueId;
  }
}
