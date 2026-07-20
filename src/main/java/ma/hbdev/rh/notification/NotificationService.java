package ma.hbdev.rh.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.config.ConfigurationService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
class NotificationService {

  private static final int RETENTION_JOURS_PAR_DEFAUT = 90;
  private static final int TAILLE_PAGE_MAX = 100;

  private final NotificationInAppRepository repository;
  private final ConfigurationService configurationService;

  @Transactional(readOnly = true)
  Page<NotificationInApp> lister(int page, int size) {
    int pageSecurisee = Math.max(0, page);
    int tailleSecurisee = Math.max(1, Math.min(size, TAILLE_PAGE_MAX));
    return repository.findByDestinataireIdAndArchiveeLeIsNull(
        utilisateurCourant(),
        PageRequest.of(pageSecurisee, tailleSecurisee, Sort.by(Sort.Direction.DESC, "creeLe")));
  }

  @Transactional(readOnly = true)
  long compterNonLues() {
    return repository.countByDestinataireIdAndLuFalseAndArchiveeLeIsNull(utilisateurCourant());
  }

  NotificationInApp marquerLue(UUID id) {
    NotificationInApp notification =
        repository
            .findByIdAndDestinataireIdAndArchiveeLeIsNull(id, utilisateurCourant())
            .orElseThrow(NotificationIntrouvableException::new);
    notification.marquerLue();
    return notification;
  }

  int marquerToutesLues() {
    return repository.marquerToutesLues(utilisateurCourant(), Instant.now());
  }

  @Scheduled(cron = "${app.notifications.archivage-cron:0 15 2 * * *}")
  int archiverExpirees() {
    int retentionJours =
        Math.max(
            1,
            configurationService.getInteger(
                "retention_notifications_in_app_jours", RETENTION_JOURS_PAR_DEFAUT));
    Instant maintenant = Instant.now();
    int nombre =
        repository.archiverAvant(maintenant.minus(retentionJours, ChronoUnit.DAYS), maintenant);
    if (nombre > 0) {
      log.info("{} notification(s) in-app archivee(s)", nombre);
    }
    return nombre;
  }

  private UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }
}
