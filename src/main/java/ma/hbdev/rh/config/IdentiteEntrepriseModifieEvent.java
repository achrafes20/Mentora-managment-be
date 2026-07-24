package ma.hbdev.rh.config;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/** EF-CFG-02 : journalisé à chaque modification — jamais délégable (EF-AUTH-12). */
record IdentiteEntrepriseModifieEvent(UUID identiteId) implements EvenementMetier {

  @Override
  public String action() {
    return "config.identite_entreprise.modifiee";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.configuration;
  }

  @Override
  public String entiteType() {
    return "identite_entreprise";
  }

  @Override
  public UUID entiteId() {
    return identiteId;
  }
}
