package ma.hbdev.rh.employee;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque création/modification/désactivation — consommé plus tard par l'écouteur d'audit
 * (T3.A1). {@code nom} n'est là que pour {@link #details()} — rendre l'audit cherchable par nom de
 * département (EF-CFG-04).
 */
public record DepartementModifieEvent(UUID departementId, String action, String nom)
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

  @Override
  public Map<String, Object> details() {
    return nom == null ? Map.of() : Map.of("departement", nom);
  }
}
