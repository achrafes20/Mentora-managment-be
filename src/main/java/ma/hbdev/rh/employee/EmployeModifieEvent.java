package ma.hbdev.rh.employee;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * Publié à chaque création/modification/transfert/désactivation — consommé par l'écouteur d'audit
 * (T3.A1).
 */
public record EmployeModifieEvent(UUID employeId, String action, NotificationMetier notification)
    implements EvenementMetier {

  public EmployeModifieEvent(UUID employeId, String action) {
    this(employeId, action, null);
  }

  public static EmployeModifieEvent creation(UUID employeId, UUID managerId, String nomComplet) {
    NotificationMetier notification =
        managerId == null
            ? null
            : NotificationMetier.creer(
                managerId,
                "employe_creation",
                "Nouvel employe dans votre equipe",
                "La fiche de " + nomComplet + " vient d'etre creee.",
                "/employes/" + employeId);
    return new EmployeModifieEvent(employeId, "creation", notification);
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
}
