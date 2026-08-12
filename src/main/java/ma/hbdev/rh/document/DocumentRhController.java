package ma.hbdev.rh.document;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasRole('ADMIN')")
class DocumentRhController {

  private final DocumentRhService documentRhService;
  private final NotificationPlanifieeRepository notificationPlanifieeRepository;
  private final SurveillancePlanifieeService surveillancePlanifieeService;

  DocumentRhController(
      DocumentRhService documentRhService,
      NotificationPlanifieeRepository notificationPlanifieeRepository,
      SurveillancePlanifieeService surveillancePlanifieeService) {
    this.documentRhService = documentRhService;
    this.notificationPlanifieeRepository = notificationPlanifieeRepository;
    this.surveillancePlanifieeService = surveillancePlanifieeService;
  }

  // Lecture seule (statut "déjà envoyé") ouverte au Manager, dans son propre département — le
  // service vérifie le périmètre via EmployeService#recuperer avant d'interroger l'historique.
  // L'envoi/renvoi reste réservé à l'Admin (@PreAuthorize de classe).
  @GetMapping("/employes/{employeId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  ApiResponse<List<EnvoiDocumentResponse>> listerEnvois(@PathVariable UUID employeId) {
    return ApiResponse.ok(documentRhService.listerEnvois(employeId));
  }

  // Fenêtre par défaut resserrée à 10 jours (au lieu de 30) : un pic de stagiaires l'été peut
  // produire des dizaines d'échéances simultanées, une fenêtre large noie alors l'urgent sous le
  // lointain. `jours` reste ajustable par l'Admin pour élargir la vue au besoin. Toujours triée
  // par échéance croissante par défaut (le plus urgent en premier), sauf tri explicite demandé.
  @GetMapping("/surveillance")
  ApiResponse<PagedResponse<NotificationPlanifieeReponse>> listerSurveillance(
      @RequestParam(required = false, defaultValue = "10") int jours, Pageable pageable) {
    Pageable pageableEffectif =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("dateEcheance").ascending());
    var page =
        notificationPlanifieeRepository
            .findByStatutAndDateEcheanceLessThanEqual(
                StatutNotificationPlanifiee.planifiee,
                LocalDate.now().plusDays(jours),
                pageableEffectif)
            .map(NotificationPlanifieeReponse::depuis);
    return ApiResponse.ok(PagedResponse.of(page));
  }

  // Déclenchement manuel du balayage par un Admin authentifié (bouton "Forcer exécution" côté
  // écran Documents) — distinct de /api/internal/surveillance/run (SurveillanceController), qui
  // reste réservé au cron n8n via InternalWebhookGuard. Ce secret partagé est documenté comme
  // n'étant "jamais" destiné à un utilisateur connecté (cf. InternalWebhookGuard) ; il ne doit
  // donc jamais être embarqué côté frontend, même pour un usage Admin volontaire.
  @PostMapping("/surveillance/executer")
  ApiResponse<Void> executerSurveillance() {
    surveillancePlanifieeService.executerSurveillance();
    return ApiResponse.ok();
  }

  // EF-DOC : aperçu du PDF avant confirmation d'envoi — même génération, sans e-mail ni
  // persistance d'un EnvoiDocument. Rendu inline (pas Content-Disposition: attachment) pour
  // s'ouvrir directement dans un onglet côté frontend, comme les PDF déjà envoyés. Ouvert au
  // Manager (périmètre département via EmployeService#recuperer) — l'envoi reste Admin seul.
  @GetMapping(value = "/employes/{employeId}/certificat-stage/apercu", produces = "application/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  ResponseEntity<byte[]> apercuCertificatStage(
      @PathVariable UUID employeId, @RequestParam(required = false) String sujetStage) {
    return reponsePdf(documentRhService.apercuCertificatStage(employeId, sujetStage));
  }

  @GetMapping(
      value = "/employes/{employeId}/certificat-travail/apercu",
      produces = "application/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  ResponseEntity<byte[]> apercuCertificatTravail(@PathVariable UUID employeId) {
    return reponsePdf(documentRhService.apercuCertificatTravail(employeId));
  }

  @GetMapping(
      value = "/employes/{employeId}/attestation-travail/apercu",
      produces = "application/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  ResponseEntity<byte[]> apercuAttestationTravail(@PathVariable UUID employeId) {
    return reponsePdf(documentRhService.apercuAttestationTravail(employeId));
  }

  @GetMapping(
      value = "/employes/{employeId}/attestation-salaire/apercu",
      produces = "application/pdf")
  @PreAuthorize("hasRole('ADMIN')")
  ResponseEntity<byte[]> apercuAttestationSalaire(@PathVariable UUID employeId) {
    return reponsePdf(documentRhService.apercuAttestationSalaire(employeId));
  }

  private ResponseEntity<byte[]> reponsePdf(byte[] pdf) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf);
  }

  @PostMapping("/employes/{employeId}/certificat-stage")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<EnvoiDocumentResponse> envoyerCertificatStage(
      @PathVariable UUID employeId, @RequestBody(required = false) CertificatStageRequete requete) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    String sujetStage = requete == null ? null : requete.sujetStage();
    EnvoiDocument envoi =
        documentRhService.envoyerCertificatStage(employeId, sujetStage, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }

  @PostMapping("/employes/{employeId}/certificat-travail")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<EnvoiDocumentResponse> envoyerCertificatTravail(@PathVariable UUID employeId) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    EnvoiDocument envoi =
        documentRhService.envoyerCertificatTravail(employeId, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }

  @PostMapping("/employes/{employeId}/attestation-travail")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<EnvoiDocumentResponse> envoyerAttestationTravail(@PathVariable UUID employeId) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    EnvoiDocument envoi =
        documentRhService.envoyerAttestationTravail(employeId, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }

  @PostMapping("/employes/{employeId}/attestation-salaire")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<EnvoiDocumentResponse> envoyerAttestationSalaire(@PathVariable UUID employeId) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    EnvoiDocument envoi =
        documentRhService.envoyerAttestationSalaire(employeId, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }

  @PostMapping("/employes/{employeId}/document-libre")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<EnvoiDocumentResponse> envoyerDocumentLibre(
      @PathVariable UUID employeId, @RequestParam("file") MultipartFile file) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    EnvoiDocument envoi =
        documentRhService.envoyerDocumentLibre(employeId, file, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }

  @PostMapping("/surveillance/{notifId}/renvoyer")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<EnvoiDocumentResponse> renvoyerDocumentSurveillance(@PathVariable UUID notifId) {
    UUID utilisateurConnecteId = CurrentUser.id().orElse(null);
    EnvoiDocument envoi =
        documentRhService.renvoyerDepuisSurveillance(notifId, utilisateurConnecteId);
    return ApiResponse.ok(EnvoiDocumentResponse.depuis(envoi));
  }
}
