package ma.hbdev.rh.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.auth.UserService;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.employee.EmployeReponse;
import ma.hbdev.rh.employee.EmployeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SurveillancePlanifieeServiceTest {

  private NotificationPlanifieeRepository repository;
  private EmployeService employeService;
  private UserService userService;
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  private RestClient.ResponseSpec responseSpec;
  private SurveillancePlanifieeService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationPlanifieeRepository.class);
    employeService = mock(EmployeService.class);

    // Stub RestClient.Builder to avoid actual HTTP calls
    RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    when(restClientBuilder.build()).thenReturn(restClient);
    // RETURNS_SELF plutôt que des matchers un par un (uri(anyString()), body(any())...) : l'API
    // fluide de RestClient a plusieurs surcharges de body() (Object, StreamingHttpOutputMessage),
    // et un matcher any() imprécis peut résoudre vers la mauvaise surcharge côté compilateur —
    // silencieusement raté (le maillon suivant de la chaîne redevient null). RETURNS_SELF fait
    // toujours renvoyer le mock lui-même, quelle que soit la surcharge réellement appelée.
    requestBodyUriSpec =
        mock(RestClient.RequestBodyUriSpec.class, org.mockito.Answers.RETURNS_SELF);
    responseSpec = mock(RestClient.ResponseSpec.class);
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

    // Les alertes partent vers les comptes Admin actifs, lus en base au moment du balayage.
    userService = mock(UserService.class);
    when(userService.emailsAdminsActifs()).thenReturn(List.of("admin-test@hbdev.ma"));

    service =
        new SurveillancePlanifieeService(
            repository, employeService, restClientBuilder, userService, "http://localhost:5678");
  }

  @Test
  void testGererEvenementEmploye_Stage() {
    UUID employeId = UUID.randomUUID();
    EmployeReponse employe =
        new EmployeReponse(
            employeId,
            "Nom",
            "Prenom",
            "email",
            "tel",
            "poste",
            UUID.randomUUID(),
            "dept",
            null,
            LocalDate.now(),
            "STAGIAIRE",
            null,
            LocalDate.now().plusDays(10),
            null,
            null,
            "actif",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(new EmployeModifieEvent(employeId, "creation", "Test Employe"));

    verify(repository).save(any(NotificationPlanifiee.class));
  }

  // Reproduit le bug corrigé (EF-DOC-12) : dateFinContratPrevue est réservé aux CDD (CHECK en
  // base + validation service), donc toujours null pour un STAGIAIRE en usage réel — avant le
  // correctif, la branche stage lisait ce champ et ne pouvait donc jamais créer de notification.
  @Test
  void testGererEvenementEmploye_StageSansDateFinStage_neCreeAucuneNotification() {
    UUID employeId = UUID.randomUUID();
    EmployeReponse employe =
        new EmployeReponse(
            employeId,
            "Nom",
            "Prenom",
            "email",
            "tel",
            "poste",
            UUID.randomUUID(),
            "dept",
            null,
            LocalDate.now(),
            "STAGIAIRE",
            null,
            null,
            null,
            null,
            "actif",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(new EmployeModifieEvent(employeId, "creation", "Test Employe"));

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
        .save(any(NotificationPlanifiee.class));
  }

  // Bug corrigé : une échéance CDD/stage se terminant sous 3 jours ouvrés faisait tomber
  // l'échéance J-3/J-15 calculée dans le passé, et creerNotification() abandonnait alors
  // silencieusement la création — aucune alerte n'était jamais programmée pour ces employés,
  // et "Forcer exécution Cron" ne pouvait pas la rattraper (il ne rejoue que les notifications
  // déjà en base). On ramène désormais l'échéance à aujourd'hui tant que la date de fin réelle
  // n'est pas encore passée.
  @Test
  void testGererEvenementEmploye_StageFinitAujourdHui_creeNotificationEcheanceAujourdHui() {
    UUID employeId = UUID.randomUUID();
    EmployeReponse employe =
        new EmployeReponse(
            employeId,
            "Nom",
            "Prenom",
            "email",
            "tel",
            "poste",
            UUID.randomUUID(),
            "dept",
            null,
            LocalDate.now(),
            "STAGIAIRE",
            null,
            LocalDate.now(),
            null,
            null,
            "actif",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(
        new EmployeModifieEvent(employeId, "modification", "Test Employe"));

    org.mockito.ArgumentCaptor<NotificationPlanifiee> captor =
        org.mockito.ArgumentCaptor.forClass(NotificationPlanifiee.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDateEcheance()).isEqualTo(LocalDate.now());
  }

  private EmployeReponse employeStagiaire(UUID employeId) {
    return new EmployeReponse(
        employeId,
        "Nom",
        "Prenom",
        "email",
        "tel",
        "poste",
        UUID.randomUUID(),
        "dept",
        null,
        LocalDate.now(),
        "STAGIAIRE",
        null,
        LocalDate.now().plusDays(3),
        null,
        null,
        "actif",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void executerSurveillance_envoiReussi_marqueEnvoyeeEtSauvegarde() {
    UUID employeId = UUID.randomUUID();
    NotificationPlanifiee notif =
        new NotificationPlanifiee(employeId, TypeFinSurveillee.fin_stage, LocalDate.now());
    when(repository.findByStatutAndDateEcheanceLessThanEqual(
            StatutNotificationPlanifiee.planifiee, LocalDate.now()))
        .thenReturn(List.of(notif));
    when(employeService.recuperer(employeId)).thenReturn(employeStagiaire(employeId));
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    service.executerSurveillance();

    assertThat(notif.getStatut()).isEqualTo(StatutNotificationPlanifiee.envoyee);
    verify(repository).saveAll(List.of(notif));
  }

  // Régression : avant le correctif, marquerEnvoyee()/marquerRelancee() étaient appelés AVANT
  // l'envoi effectif et l'échec du webhook était avalé silencieusement — la notification était
  // donc sauvegardée comme "envoyée" alors qu'aucun e-mail n'était réellement parti, et ne
  // repartait plus jamais au balayage suivant.
  @Test
  void executerSurveillance_echecWebhook_neMarquePasEnvoyeeEtNeSauvegardeRien() {
    UUID employeId = UUID.randomUUID();
    NotificationPlanifiee notif =
        new NotificationPlanifiee(employeId, TypeFinSurveillee.fin_stage, LocalDate.now());
    when(repository.findByStatutAndDateEcheanceLessThanEqual(
            StatutNotificationPlanifiee.planifiee, LocalDate.now()))
        .thenReturn(List.of(notif));
    when(employeService.recuperer(employeId)).thenReturn(employeStagiaire(employeId));
    when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("n8n indisponible"));

    service.executerSurveillance();

    assertThat(notif.getStatut()).isEqualTo(StatutNotificationPlanifiee.planifiee);
    verify(repository).saveAll(List.of());
  }
}
