package ma.hbdev.rh.administrative;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/** EF-ADM-11 : journalisé à chaque modification — jamais délégable (gestion Admin uniquement). */
record PolitiqueCongeModifieeEvent(String typeContrat) implements EvenementMetier {

  @Override
  public String action() {
    return "politique_conges.modifiee";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.demande_administrative;
  }

  @Override
  public String entiteType() {
    return "politique_conges";
  }

  @Override
  public UUID entiteId() {
    return null;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("typeContrat", typeContrat);
  }
}
