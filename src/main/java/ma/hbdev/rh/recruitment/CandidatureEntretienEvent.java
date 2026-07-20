package ma.hbdev.rh.recruitment;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * EF-REC-08 : publié quand une candidature passe à l'étape "Entretien" — consommé plus tard par
 * l'écouteur Mattermost (T3.A1) pour notifier {@code managerId} et lui rendre la fiche accessible.
 */
public record CandidatureEntretienEvent(
    UUID candidatureId, UUID managerId, NotificationMetier notification)
    implements EvenementMetier {

  public CandidatureEntretienEvent(UUID candidatureId, UUID managerId) {
    this(candidatureId, managerId, creerNotification(candidatureId, managerId, null));
  }

  public static CandidatureEntretienEvent pourCandidat(
      UUID candidatureId, UUID managerId, String nomComplet) {
    return new CandidatureEntretienEvent(
        candidatureId, managerId, creerNotification(candidatureId, managerId, nomComplet));
  }

  private static NotificationMetier creerNotification(
      UUID candidatureId, UUID managerId, String nomComplet) {
    String candidat = nomComplet == null || nomComplet.isBlank() ? "un candidat" : nomComplet;
    return NotificationMetier.creer(
        managerId,
        "candidature_entretien",
        "Nouvel entretien a preparer",
        "La candidature de " + candidat + " vous a ete attribuee.",
        "/recrutement/" + candidatureId);
  }

  @Override
  public String action() {
    return "entretien_planifie";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.recrutement;
  }

  @Override
  public String entiteType() {
    return "candidature";
  }

  @Override
  public UUID entiteId() {
    return candidatureId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("managerId", managerId);
  }
}
