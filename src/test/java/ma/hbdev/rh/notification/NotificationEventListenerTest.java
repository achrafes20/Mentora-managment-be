package ma.hbdev.rh.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.shared.mattermost.MattermostClient;
import ma.hbdev.rh.shared.mattermost.ResultatMattermost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

  @Mock private NotificationInAppRepository inAppRepository;
  @Mock private NotificationMattermostRepository mattermostRepository;
  @Mock private MattermostClient mattermostClient;

  @InjectMocks private InAppNotificationEventListener inAppListener;
  @InjectMocks private MattermostNotificationEventListener mattermostListener;

  @Test
  void creeSystematiquementLaNotificationInAppDestineeAuManager() {
    UUID employeId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    EmployeModifieEvent evenement =
        EmployeModifieEvent.creation(employeId, managerId, "Sara Amrani");

    inAppListener.creerNotification(evenement);

    ArgumentCaptor<NotificationInApp> captor = ArgumentCaptor.forClass(NotificationInApp.class);
    verify(inAppRepository).save(captor.capture());
    NotificationInApp notification = captor.getValue();
    assertThat(notification.getDestinataireId()).isEqualTo(managerId);
    assertThat(notification.getEntiteId()).isEqualTo(employeId);
    assertThat(notification.getLienAction()).isEqualTo("/employes/" + employeId);
    assertThat(notification.isLu()).isFalse();
  }

  @Test
  void ignoreUnEvenementQuiNePortePasDeNotificationFonctionnelle() {
    inAppListener.creerNotification(new EmployeModifieEvent(UUID.randomUUID(), "modification"));

    org.mockito.Mockito.verifyNoInteractions(inAppRepository);
  }

  @Test
  void journaliseLEchecMattermostSansSupprimerLaNotificationInApp() {
    EmployeModifieEvent evenement =
        EmployeModifieEvent.creation(UUID.randomUUID(), UUID.randomUUID(), "Sara Amrani");
    NotificationInApp inApp =
        new NotificationInApp(
            evenement.notification(),
            evenement.module(),
            evenement.entiteType(),
            evenement.entiteId());
    when(mattermostClient.envoyerMessagePrive(
            eq(evenement.notification().destinataireId()), any(String.class)))
        .thenReturn(ResultatMattermost.echec("service indisponible"));
    when(inAppRepository.findById(evenement.notification().id())).thenReturn(Optional.of(inApp));

    mattermostListener.envoyer(evenement);

    verify(mattermostRepository).save(any(NotificationMattermost.class));
    assertThat(inApp.isMattermostTente()).isTrue();
    assertThat(inApp.getMattermostReussi()).isFalse();
  }
}
