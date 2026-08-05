package ma.hbdev.rh.attendance;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * EF-ATT-11 : escalade sur récurrence — le nombre d'anomalies non résolues d'un employé sur la
 * période configurée a atteint le seuil. Notifie le Manager du département concerné, sur le même
 * principe que les autres notifications métier (EF-REC-08, EF-ADM-08 — destinataire unique).
 */
record SeuilAnomaliesDepasseEvent(
    UUID employeId, String employeNomComplet, UUID managerId, int nombreAnomalies, int seuil)
    implements EvenementMetier {

  @Override
  public String action() {
    return "presence.seuil_anomalies_depasse";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.presence;
  }

  @Override
  public String entiteType() {
    return "employe";
  }

  @Override
  public UUID entiteId() {
    return employeId;
  }

  /**
   * Alerte calculée automatiquement (franchissement de seuil), pas une décision d'une personne —
   * notifiée au Manager (ci-dessous) mais volontairement absente de journal_audit, qui ne doit
   * tracer que des actions attribuables à quelqu'un.
   */
  @Override
  public boolean audite() {
    return false;
  }

  @Override
  public NotificationMetier notification() {
    if (managerId == null) {
      return null;
    }
    return NotificationMetier.creer(
        managerId,
        "seuil_anomalies_depasse",
        "Seuil d'anomalies dépassé",
        employeNomComplet
            + " cumule "
            + nombreAnomalies
            + " anomalies non résolues (seuil : "
            + seuil
            + ").",
        "/presence");
  }
}
