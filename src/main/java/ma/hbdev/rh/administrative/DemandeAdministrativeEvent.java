package ma.hbdev.rh.administrative;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

record DemandeAdministrativeEvent(
    UUID demandeId, String action, UUID managerId, String employeNomComplet)
    implements EvenementMetier {

  @Override
  public ModuleAudit module() {
    return ModuleAudit.demande_administrative;
  }

  @Override
  public String action() {
    return action;
  }

  @Override
  public String entiteType() {
    return "demande_administrative";
  }

  @Override
  public UUID entiteId() {
    return demandeId;
  }

  /** EF-AUTH-14 : approbation/rejet/annulation sont les décisions déléguables sur une demande. */
  @Override
  public boolean decisionDelegable() {
    return "approbation".equals(action) || "rejet".equals(action) || "annulation".equals(action);
  }

  @Override
  public NotificationMetier notification() {
    if (managerId == null || !("approbation".equals(action) || "rejet".equals(action))) {
      return null;
    }
    String decision = "approbation".equals(action) ? "approuvee" : "rejetee";
    return NotificationMetier.creer(
        managerId,
        "demande_decidee",
        "Demande administrative " + decision,
        "La demande de " + employeNomComplet + " a ete " + decision + ".",
        "/demandes");
  }
}
