package ma.hbdev.rh.shared.event;

import java.util.Objects;
import java.util.UUID;

/** Description immutable des deux notifications (in-app et Mattermost) liees a un evenement. */
public record NotificationMetier(
    UUID id,
    UUID destinataireId,
    String typeEvenement,
    String titre,
    String message,
    String lienAction) {

  public NotificationMetier {
    id = id == null ? UUID.randomUUID() : id;
    Objects.requireNonNull(destinataireId, "destinataireId");
    Objects.requireNonNull(typeEvenement, "typeEvenement");
    Objects.requireNonNull(titre, "titre");
    Objects.requireNonNull(message, "message");
  }

  public static NotificationMetier creer(
      UUID destinataireId, String typeEvenement, String titre, String message, String lienAction) {
    return new NotificationMetier(
        UUID.randomUUID(), destinataireId, typeEvenement, titre, message, lienAction);
  }
}
