package ma.hbdev.rh.employee;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque exécution réelle (hors dry-run) d'un lot d'import — consommé par l'écouteur
 * d'audit (T3.A1).
 */
public record ImportExecuteEvent(UUID lotId, String cible) implements EvenementMetier {
  @Override
  public String action() {
    return "execution";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.employe;
  }

  @Override
  public String entiteType() {
    return "import_lot";
  }

  @Override
  public UUID entiteId() {
    return lotId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("cible", cible);
  }
}
