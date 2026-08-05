package ma.hbdev.rh.employee;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * Publié à chaque création/modification/transfert/désactivation — consommé par l'écouteur d'audit
 * (T3.A1). {@code nomComplet} n'est là que pour {@link #details()} — rendre l'audit cherchable par
 * nom d'employé, pas seulement par UUID opaque (sinon injoignable via la recherche pg_trgm de
 * EF-CFG-04).
 */
public record EmployeModifieEvent(
    UUID employeId, String action, String nomComplet, NotificationMetier notification)
    implements EvenementMetier {

  public EmployeModifieEvent(UUID employeId, String action, String nomComplet) {
    this(employeId, action, nomComplet, null);
  }

  public static EmployeModifieEvent creation(UUID employeId, UUID managerId, String nomComplet) {
    NotificationMetier notification =
        managerId == null
            ? null
            : NotificationMetier.creer(
                managerId,
                "employe_creation",
                "Nouvel employé dans votre équipe",
                "La fiche de " + nomComplet + " vient d'être créée.",
                "/employes/" + employeId);
    return new EmployeModifieEvent(employeId, "creation", nomComplet, notification);
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.employe;
  }

  @Override
  public String entiteType() {
    return "employe";
  }

  @Override
  public UUID entiteId() {
    return employeId;
  }

  @Override
  public Map<String, Object> details() {
    return nomComplet == null ? Map.of() : Map.of("employe", nomComplet);
  }
}
