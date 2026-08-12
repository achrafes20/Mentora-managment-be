package ma.hbdev.rh.document;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.auth.UserService;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.employee.EmployeReponse;
import ma.hbdev.rh.employee.EmployeService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.mail.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@Transactional
@Slf4j
class SurveillancePlanifieeService {

  private final NotificationPlanifieeRepository notificationPlanifieeRepository;
  private final EmployeService employeService;
  private final UserService userService;
  private final RestClient restClient;
  private final String webhookUrl;

  SurveillancePlanifieeService(
      NotificationPlanifieeRepository notificationPlanifieeRepository,
      EmployeService employeService,
      RestClient.Builder restClientBuilder,
      UserService userService,
      @Value("${app.n8n.base-url:http://localhost:5678}") String n8nBaseUrl) {
    this.notificationPlanifieeRepository = notificationPlanifieeRepository;
    this.employeService = employeService;
    this.userService = userService;
    this.restClient =
        RestClientFactory.buildWithTimeouts(
            restClientBuilder, Duration.ofSeconds(5), Duration.ofSeconds(10));
    this.webhookUrl = MailService.urlWebhookNotifyEmail(n8nBaseUrl);
  }

  @EventListener
  void gererEvenementEmploye(EmployeModifieEvent event) {
    EmployeReponse employe;
    try {
      employe = employeService.recuperer(event.employeId());
    } catch (Exception e) {
      return; // introuvable
    }

    // Toujours annuler les notifications en attente pour repartir à zéro
    annulerNotificationsEnAttente(employe.id());

    // Si inactif ou ni CDD ni STAGIAIRE, on s'arrête
    if ("inactif".equals(employe.statut())) {
      return;
    }

    String typeContrat = employe.typeContrat();
    if (typeContrat == null
        || (!"CDD".equals(typeContrat) && !typeContrat.startsWith("STAGIAIRE"))) {
      return; // CDI etc.
    }

    if (typeContrat.startsWith("STAGIAIRE")) {
      LocalDate dateFinStage = employe.dateFinStagePrevue();
      if (dateFinStage != null) {
        creerNotification(
            employe.id(),
            TypeFinSurveillee.fin_stage,
            soustraireJoursOuvres(dateFinStage, 3),
            dateFinStage);
      }
    } else if ("CDD".equals(typeContrat)) {
      LocalDate dateFinCdd = employe.dateFinContratPrevue();
      if (dateFinCdd != null) {
        creerNotification(
            employe.id(),
            TypeFinSurveillee.fin_cdd,
            soustraireJoursOuvres(dateFinCdd, 15),
            dateFinCdd);
        creerNotification(
            employe.id(),
            TypeFinSurveillee.fin_cdd,
            soustraireJoursOuvres(dateFinCdd, 3),
            dateFinCdd);
      }
    }
  }

  /**
   * EF-DOC-12/13/14 : déclencheur automatique du balayage, une fois par jour.
   *
   * <p>Sans lui, {@link #executerSurveillance()} n'était appelable qu'à la main via {@code POST
   * /api/internal/surveillance/run} — les notifications J-3 / J-15 de fin de contrat étaient donc
   * planifiées en base mais jamais envoyées.
   *
   * <p>Choix du mécanisme : {@code @Scheduled} Spring plutôt qu'un 4ᵉ workflow n8n, pour rejoindre
   * le mécanisme déjà majoritaire dans le backend ({@code AnomalieService}, {@code
   * DelegationService}, {@code NotificationService}) et éviter qu'une fonctionnalité RH dépende de
   * la disponibilité de n8n. L'endpoint HTTP reste en place pour les rejeux manuels.
   *
   * <p>{@code zone} explicite (Africa/Casablanca) : le Maroc suspend l'heure d'été pendant le
   * Ramadan, on ne se repose jamais sur le fuseau par défaut de la JVM.
   */
  @Scheduled(cron = "${app.documents.surveillance-cron:0 0 3 * * *}", zone = "Africa/Casablanca")
  void balayageQuotidien() {
    executerSurveillance();
  }

  public void executerSurveillance() {
    LocalDate aujourdHui = LocalDate.now();
    List<NotificationPlanifiee> aTraiter =
        notificationPlanifieeRepository.findByStatutAndDateEcheanceLessThanEqual(
            StatutNotificationPlanifiee.planifiee, aujourdHui);

    if (aTraiter.isEmpty()) {
      return;
    }

    // Destinataires lus en base à chaque balayage : le compte Admin est la source de vérité
    // de sa propre adresse, il n'y a donc rien à tenir à jour en configuration.
    List<String> destinataires = userService.emailsAdminsActifs();
    if (destinataires.isEmpty()) {
      // Ne pas marquer les notifications comme envoyées : sans destinataire, rien n'est parti.
      // Elles restent "planifiee" et repartiront au prochain balayage, une fois un Admin actif.
      log.warn(
          "Surveillance des fins de contrat : aucun compte Admin actif, {} notification(s)"
              + " laissée(s) en attente.",
          aTraiter.size());
      return;
    }

    // Seules les notifications réellement envoyées sont marquées puis sauvegardées : si le
    // webhook échoue pour une notification (n8n/Mailpit hors service, timeout...), elle doit
    // rester "planifiee" pour repartir au prochain balayage plutôt que d'être marquée envoyée à
    // tort (bug corrigé : avant, marquerEnvoyee()/marquerRelancee() étaient appelés AVANT
    // l'envoi effectif, et l'échec du webhook était avalé silencieusement — la notification ne
    // repartait donc jamais malgré un e-mail jamais parti).
    List<NotificationPlanifiee> traitees = new ArrayList<>();
    for (NotificationPlanifiee notif : aTraiter) {
      try {
        EmployeReponse employe = employeService.recuperer(notif.getEmployeId());
        String sujet;
        String message;
        boolean relance = false;

        if (notif.getTypeSurveillance() == TypeFinSurveillee.fin_stage) {
          sujet = "Fin de stage proche : " + employe.prenom() + " " + employe.nom();
          message =
              "Bonjour,\n\nLe stage de "
                  + employe.prenom()
                  + " "
                  + employe.nom()
                  + " (poste: "
                  + employe.poste()
                  + ") se termine le "
                  + employe.dateFinStagePrevue()
                  + " (J-3).\n\nVeuillez préparer le certificat de stage.\n\nCordialement,\nMentora RH";
        } else {
          boolean dejaEnvoye =
              notificationPlanifieeRepository.existsByEmployeIdAndStatut(
                      notif.getEmployeId(), StatutNotificationPlanifiee.envoyee)
                  || notificationPlanifieeRepository.existsByEmployeIdAndStatut(
                      notif.getEmployeId(), StatutNotificationPlanifiee.relancee);

          if (dejaEnvoye) {
            relance = true;
            sujet = "RAPPEL : Fin de CDD proche : " + employe.prenom() + " " + employe.nom();
            message =
                "Bonjour,\n\nCeci est un rappel : le CDD de "
                    + employe.prenom()
                    + " "
                    + employe.nom()
                    + " (poste: "
                    + employe.poste()
                    + ") se termine le "
                    + employe.dateFinContratPrevue()
                    + " (J-3).\n\nVeuillez préparer le certificat de travail.\n\nCordialement,\nMentora RH";
          } else {
            sujet = "Fin de CDD proche : " + employe.prenom() + " " + employe.nom();
            message =
                "Bonjour,\n\nLe CDD de "
                    + employe.prenom()
                    + " "
                    + employe.nom()
                    + " (poste: "
                    + employe.poste()
                    + ") se termine le "
                    + employe.dateFinContratPrevue()
                    + " (J-15).\n\nVeuillez préparer le certificat de travail.\n\nCordialement,\nMentora RH";
          }
        }

        for (String destinataire : destinataires) {
          envoyerEmailViaWebhook(destinataire, sujet, message);
        }

        if (relance) {
          notif.marquerRelancee();
        } else {
          notif.marquerEnvoyee();
        }
        traitees.add(notif);
      } catch (Exception e) {
        log.error(
            "Échec du traitement de la notification {} (webhook n8n indisponible ?) — reste"
                + " planifiée, repartira au prochain balayage",
            notif.getId(),
            e);
      }
    }
    notificationPlanifieeRepository.saveAll(traitees);
  }

  private void envoyerEmailViaWebhook(String to, String subject, String message) {
    record N8nPayload(String to, String subject, String message) {}
    try {
      restClient
          .post()
          .uri(webhookUrl)
          .body(new N8nPayload(to, subject, message))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      throw new EnvoiWebhookEchoueException(
          "Échec de l'envoi de la notification de surveillance via le webhook n8n : "
              + e.getMessage(),
          e);
    }
  }

  private void annulerNotificationsEnAttente(UUID employeId) {
    List<NotificationPlanifiee> enAttente =
        notificationPlanifieeRepository.findByEmployeIdAndStatutIn(
            employeId, List.of(StatutNotificationPlanifiee.planifiee));
    for (NotificationPlanifiee notif : enAttente) {
      notif.annuler();
    }
  }

  /**
   * Si l'échéance calculée (J-3/J-15 ouvrés) tombe déjà dans le passé au moment où l'employé est
   * enregistré — date de fin très proche ou modifiée tardivement — on ne l'abandonne plus
   * silencieusement : on la ramène à aujourd'hui tant que la date de fin réelle n'est pas encore
   * passée, pour que le prochain balayage l'envoie quand même (bug : sinon aucune notification
   * n'était jamais créée pour un CDD/stage se terminant sous 3 jours ouvrés).
   */
  private void creerNotification(
      UUID employeId, TypeFinSurveillee type, LocalDate echeance, LocalDate dateFin) {
    LocalDate aujourdHui = LocalDate.now();
    if (dateFin.isBefore(aujourdHui)) {
      return;
    }
    LocalDate echeanceEffective = echeance.isBefore(aujourdHui) ? aujourdHui : echeance;
    NotificationPlanifiee notif = new NotificationPlanifiee(employeId, type, echeanceEffective);
    notificationPlanifieeRepository.save(notif);
  }

  private LocalDate soustraireJoursOuvres(LocalDate date, int joursOuvres) {
    LocalDate res = date;
    int ajoutes = 0;
    while (ajoutes < joursOuvres) {
      res = res.minusDays(1);
      int dayOfWeek = res.getDayOfWeek().getValue();
      if (dayOfWeek < 6) { // Lundi à Vendredi
        ajoutes++;
      }
    }
    return res;
  }
}
