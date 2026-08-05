package ma.hbdev.rh.document;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import ma.hbdev.rh.employee.EmployeService;
import ma.hbdev.rh.shared.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;

class DocumentRhServiceTest {

  private EnvoiDocumentRhRepository envoiDocumentRepository;
  private EmployeService employeService;
  private CertificatGenerator certificatGenerator;
  private FileStorageService fileStorageService;
  private NotificationPlanifieeRepository notificationPlanifieeRepository;
  private ApplicationEventPublisher evenements;
  private DocumentRhService service;

  @BeforeEach
  void setUp() {
    envoiDocumentRepository = mock(EnvoiDocumentRhRepository.class);
    employeService = mock(EmployeService.class);
    certificatGenerator = mock(CertificatGenerator.class);
    fileStorageService = mock(FileStorageService.class);
    notificationPlanifieeRepository = mock(NotificationPlanifieeRepository.class);
    evenements = mock(ApplicationEventPublisher.class);

    RestClient.Builder builder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(builder.requestFactory(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
    when(builder.build()).thenReturn(restClient);
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.contentType(org.mockito.ArgumentMatchers.any()))
        .thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.body(org.mockito.ArgumentMatchers.any()))
        .thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("boom"));

    service =
        new DocumentRhService(
            envoiDocumentRepository,
            employeService,
            certificatGenerator,
            fileStorageService,
            builder,
            "http://localhost:5678",
            "http://localhost:8080",
            notificationPlanifieeRepository,
            evenements);
  }

  @Test
  void renvoyerDepuisSurveillance_shouldThrowWhenWebhookFails() {
    UUID notifId = UUID.randomUUID();
    UUID employeId = UUID.randomUUID();
    UUID envoyeurId = UUID.randomUUID();

    NotificationPlanifiee notif =
        new NotificationPlanifiee(
            employeId, TypeFinSurveillee.fin_stage, java.time.LocalDate.now());
    notif.marquerEnvoyee();

    when(notificationPlanifieeRepository.findById(notifId)).thenReturn(Optional.of(notif));
    when(employeService.recuperer(employeId))
        .thenReturn(
            new ma.hbdev.rh.employee.EmployeReponse(
                employeId,
                "Nom",
                "Prenom",
                "test@example.com",
                null,
                "Poste",
                null,
                null,
                null,
                java.time.LocalDate.now(),
                "CDD",
                java.time.LocalDate.now(),
                null,
                java.time.LocalDate.now(),
                null,
                "actif",
                null,
                null,
                null,
                null));
    when(certificatGenerator.genererCertificatStage(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new byte[] {1, 2, 3});
    when(fileStorageService.televerser(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new ma.hbdev.rh.shared.file.FichierUploade(
                UUID.randomUUID(), "file.pdf", "application/pdf", 3));
    when(envoiDocumentRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThrows(
        IllegalStateException.class, () -> service.renvoyerDepuisSurveillance(notifId, envoyeurId));
  }
}
