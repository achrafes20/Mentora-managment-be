package ma.hbdev.rh.recruitment;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * Publié à chaque création/modification/fermeture/réouverture d'offre — consommé par l'écouteur
 * d'audit (T3.A1), même principe que {@code DepartementModifieEvent} / {@code EmployeModifieEvent}.
 * {@code intitule} n'est là que pour {@link #details()} — rendre l'audit cherchable par intitulé
 * d'offre (EF-CFG-04).
 */
public record OffreEmploiModifieEvent(UUID offreId, String action, String intitule)
    implements EvenementMetier {
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

  @Override
  public Map<String, Object> details() {
    return intitule == null ? Map.of() : Map.of("offre", intitule);
  }
}
