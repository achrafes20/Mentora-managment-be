package ma.hbdev.rh.document;

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
  private SurveillancePlanifieeService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationPlanifieeRepository.class);
    employeService = mock(EmployeService.class);

    // Stub RestClient.Builder to avoid actual HTTP calls
    RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    when(restClientBuilder.build()).thenReturn(restClient);

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
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(new EmployeModifieEvent(employeId, "creation"));

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
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(new EmployeModifieEvent(employeId, "creation"));

    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
        .save(any(NotificationPlanifiee.class));
  }
}
