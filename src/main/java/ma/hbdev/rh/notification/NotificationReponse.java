package ma.hbdev.rh.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationReponse(
    UUID id,
    String titre,
    String message,
    String module,
    String lienAction,
    String entiteType,
    UUID entiteId,
    boolean lu,
    Instant luLe,
    boolean mattermostTente,
    Boolean mattermostReussi,
    Instant creeLe) {

  static NotificationReponse depuis(NotificationInApp notification) {
    return new NotificationReponse(
        notification.getId(),
        notification.getTitre(),
        notification.getMessage(),
        notification.getModule() == null ? null : notification.getModule().name(),
        notification.getLienAction(),
        notification.getEntiteType(),
        notification.getEntiteId(),
        notification.isLu(),
        notification.getLuLe(),
        notification.isMattermostTente(),
        notification.getMattermostReussi(),
        notification.getCreeLe());
  }
}
