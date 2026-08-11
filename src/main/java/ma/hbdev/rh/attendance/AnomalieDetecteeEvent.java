package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * EF-ATT-04 : une anomalie de pointage vient d'être détectée sur son propre pointage — distinct de
 * {@link SeuilAnomaliesDepasseEvent} (notifie le Manager, seulement au franchissement du seuil
 * configuré). Ici c'est l'employé lui-même qui est notifié, à chaque anomalie, pour qu'il sache
 * qu'un retard/départ anticipé/absence a été enregistré avant même d'en discuter avec son
 * responsable.
 *
 * <p>Notification silencieusement omise ({@link #notification()} renvoie {@code null}) si l'employé
 * n'a pas de compte applicatif ({@code employes.utilisateur_id} nullable — tous les employés n'ont
 * pas de connexion self-service), même dégradation gracieuse que {@link SeuilAnomaliesDepasseEvent}
 * pour un manager absent.
 */
record AnomalieDetecteeEvent(
    UUID employeId,
    UUID utilisateurId,
    String employeNomComplet,
    TypeAnomaliePointage type,
    LocalDate datePointage)
    implements EvenementMetier {

  @Override
  public String action() {
    return "presence.anomalie_detectee";
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

  @Override
  public NotificationMetier notification() {
    if (utilisateurId == null) {
      return null;
    }
    return NotificationMetier.creer(
        utilisateurId,
        "anomalie_pointage_detectee",
        "Anomalie de pointage",
        libelleType() + " enregistré(e) le " + datePointage + ".",
        "/presence");
  }

  private String libelleType() {
    return switch (type) {
      case retard -> "Un retard a été";
      case depart_anticipe -> "Un départ anticipé a été";
      case absence_checkout -> "Une absence de check-out a été";
      case absence_totale -> "Une absence totale a été";
    };
  }
}
