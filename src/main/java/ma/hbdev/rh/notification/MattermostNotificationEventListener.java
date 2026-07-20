package ma.hbdev.rh.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.NotificationMetier;
import ma.hbdev.rh.shared.mattermost.MattermostClient;
import ma.hbdev.rh.shared.mattermost.ResultatMattermost;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** EF-NOTIF-06 : l'echec Mattermost est persiste et ne remonte jamais au service metier. */
@Component
@RequiredArgsConstructor
@Slf4j
class MattermostNotificationEventListener {

  private final NotificationInAppRepository inAppRepository;
  private final NotificationMattermostRepository mattermostRepository;
  private final MattermostClient mattermostClient;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void envoyer(EvenementMetier evenement) {
    NotificationMetier notification = evenement.notification();
    if (notification == null) {
      return;
    }

    ResultatMattermost resultat;
    try {
      resultat =
          mattermostClient.envoyerMessagePrive(
              notification.destinataireId(), formaterMessage(notification));
    } catch (RuntimeException exception) {
      log.warn("Echec inattendu du client Mattermost", exception);
      resultat = ResultatMattermost.echec(exception.getClass().getSimpleName());
    }

    mattermostRepository.save(
        new NotificationMattermost(evenement, notification, resultat.reussi(), resultat.erreur()));
    ResultatMattermost resultatFinal = resultat;
    inAppRepository
        .findById(notification.id())
        .ifPresent(
            inApp ->
                inApp.enregistrerResultatMattermost(
                    resultatFinal.reussi(), resultatFinal.erreur()));
  }

  private String formaterMessage(NotificationMetier notification) {
    String lien = notification.lienAction() == null ? "" : "\n" + notification.lienAction();
    return "**" + notification.titre() + "**\n" + notification.message() + lien;
  }
}
