package ma.hbdev.rh.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.employee.EmployeReponse;
import ma.hbdev.rh.employee.EmployeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SurveillancePlanifieeServiceTest {

  private NotificationPlanifieeRepository repository;
  private EmployeService employeService;
  private SurveillancePlanifieeService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationPlanifieeRepository.class);
    employeService = mock(EmployeService.class);

    // Stub RestClient.Builder to avoid actual HTTP calls
    RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    when(restClientBuilder.build()).thenReturn(restClient);

    service = new SurveillancePlanifieeService(
        repository,
        employeService,
        restClientBuilder,
        "http://localhost:5678");
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
            LocalDate.now().plusDays(10),
            null,
            null,
            "actif",
            null,
            null,
            null,
            null);
    when(employeService.recuperer(employeId)).thenReturn(employe);

    service.gererEvenementEmploye(new EmployeModifieEvent(employeId, "creation"));

    verify(repository).save(any(NotificationPlanifiee.class));
  }
}
