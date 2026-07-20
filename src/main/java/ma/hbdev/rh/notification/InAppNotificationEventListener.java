package ma.hbdev.rh.notification;

import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.NotificationMetier;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** EF-NOTIF-01 : le canal in-app est cree avant toute tentative Mattermost. */
@Component
@RequiredArgsConstructor
class InAppNotificationEventListener {

  private final NotificationInAppRepository repository;

  @EventListener
  @Order(10)
  void creerNotification(EvenementMetier evenement) {
    NotificationMetier notification = evenement.notification();
    if (notification == null) {
      return;
    }
    repository.save(
        new NotificationInApp(
            notification, evenement.module(), evenement.entiteType(), evenement.entiteId()));
  }
}
