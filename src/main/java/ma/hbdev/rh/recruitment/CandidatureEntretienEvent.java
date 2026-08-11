package ma.hbdev.rh.recruitment;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * EF-REC-08 : publié quand une candidature passe à l'étape "Entretien" — consommé plus tard par
 * l'écouteur Mattermost (T3.A1) pour notifier {@code managerId} et lui rendre la fiche accessible.
 * {@code candidatNomComplet} n'est là que pour {@link #details()} — rendre l'audit cherchable par
 * nom de candidat (EF-CFG-04).
 */
public record CandidatureEntretienEvent(
    UUID candidatureId, UUID managerId, String candidatNomComplet, NotificationMetier notification)
    implements EvenementMetier {

  public static CandidatureEntretienEvent pourCandidat(
      UUID candidatureId, UUID managerId, String nomComplet) {
    return new CandidatureEntretienEvent(
        candidatureId,
        managerId,
        nomComplet,
        creerNotification(candidatureId, managerId, nomComplet));
  }

  private static NotificationMetier creerNotification(
      UUID candidatureId, UUID managerId, String nomComplet) {
    String candidat = nomComplet == null || nomComplet.isBlank() ? "un candidat" : nomComplet;
    return NotificationMetier.creer(
        managerId,
        "candidature_entretien",
        "Nouvel entretien à préparer",
        "La candidature de " + candidat + " vous a été attribuée.",
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
    return candidatNomComplet == null
        ? Map.of("managerId", managerId)
        : Map.of("managerId", managerId, "candidat", candidatNomComplet);
  }
}
