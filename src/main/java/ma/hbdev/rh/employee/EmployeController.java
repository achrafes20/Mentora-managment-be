package ma.hbdev.rh.employee;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.file.FichierInvalideException;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * EF-EMP (fiche employé). RBAC réel (T1.C1) : consultation (liste/détail/documents/historique)
 * ouverte à Admin et Manager — le Manager est restreint côté service à son propre département
 * (EF-AUTH-03, cf. {@code EmployeService}) ; création/modification/transfert/désactivation/upload
 * de document réservés à l'Admin (EF-AUTH-03 : "aucun droit de modification" pour le Manager), à
 * l'exception du sujet de stage (modifierSujetStage) ouvert au Manager de son département.
 */
@RestController
@RequestMapping("/api/employes")
public class EmployeController {

  private final EmployeService employeService;

  public EmployeController(EmployeService employeService) {
    this.employeService = employeService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<EmployeReponse>> lister(
      @RequestParam(required = false) UUID departementId,
      @RequestParam(required = false) UUID managerId,
      @RequestParam(required = false) TypeContratEmploye typeContrat,
      @RequestParam(required = false) StatutActifInactif statut,
      @RequestParam(required = false) String recherche,
      Pageable pageable) {
    var page =
        employeService
            .lister(departementId, managerId, typeContrat, statut, recherche, pageable)
            .map(EmployeReponse::depuis);
    return ApiResponse.ok(PagedResponse.of(page));
  }

  // EF-EXP-01 : réutilise les mêmes filtres/périmètre Manager que lister() — chemin statique
  // "/export", résolu avant "/{id}" par Spring (spécificité de route), pas de conflit malgré la
  // profondeur identique.
  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<byte[]> exporter(
      @RequestParam FormatExport format,
      @RequestParam(required = false) UUID departementId,
      @RequestParam(required = false) UUID managerId,
      @RequestParam(required = false) TypeContratEmploye typeContrat,
      @RequestParam(required = false) StatutActifInactif statut,
      @RequestParam(required = false) String recherche) {
    byte[] contenu =
        employeService.exporter(format, departementId, managerId, typeContrat, statut, recherche);
    String nomFichier = "employes_" + LocalDate.now() + format.extension();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(format.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nomFichier).build().toString())
        .body(contenu);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<EmployeReponse> detail(@PathVariable UUID id) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.trouver(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeReponse> creer(@Valid @RequestBody EmployeRequete requete) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.creer(requete)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeReponse> modifier(
      @PathVariable UUID id, @Valid @RequestBody EmployeModificationRequete requete) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.modifier(id, requete)));
  }

  // EF-EMP-01 : seule modification ouverte au Manager (dans son département, cf.
  // EmployeService#verifierPerimetreManager) — contrairement à modifier() ci-dessus, réservé à
  // l'Admin.
  @PutMapping("/{id}/sujet-stage")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<EmployeReponse> modifierSujetStage(
      @PathVariable UUID id, @RequestBody SujetStageRequete requete) {
    return ApiResponse.ok(
        EmployeReponse.depuis(employeService.modifierSujetStage(id, requete.sujetStage())));
  }

  @PostMapping("/{id}/transferer")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeReponse> transferer(
      @PathVariable UUID id, @Valid @RequestBody TransfertRequete requete) {
    return ApiResponse.ok(
        EmployeReponse.depuis(
            employeService.transferer(id, requete, CurrentUser.id().orElse(null))));
  }

  @GetMapping("/{id}/transferts")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<EmployeTransfertReponse>> historiqueTransferts(@PathVariable UUID id) {
    return ApiResponse.ok(
        employeService.historiqueTransferts(id).stream()
            .map(EmployeTransfertReponse::depuis)
            .toList());
  }

  @PostMapping("/{id}/desactiver")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> desactiver(
      @PathVariable UUID id, @Valid @RequestBody DesactivationRequete requete) {
    employeService.desactiver(id, requete);
    return ApiResponse.ok();
  }

  // Déclenchement manuel du balayage de désactivation automatique (bouton "Forcer l'exécution"
  // côté écran Documents), même principe que DocumentRhController#executerSurveillance — le
  // balayage tourne normalement tout seul via DesactivationAutomatiqueScheduler, ce endpoint sert
  // juste à ne pas attendre le prochain déclenchement (rattrapage, vérification en recette).
  @PostMapping("/desactivation-automatique/executer")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> executerDesactivationAutomatique() {
    employeService.desactiverContratsExpires();
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/documents")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeDocumentReponse> attacherDocument(
      @PathVariable UUID id,
      @RequestPart MultipartFile fichier,
      @RequestPart(required = false) String typeDocument) {
    UUID televersePar = CurrentUser.id().orElse(null);
    return ApiResponse.ok(employeService.attacherDocument(id, fichier, typeDocument, televersePar));
  }

  @GetMapping("/{id}/documents")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<EmployeDocumentReponse>> listerDocuments(@PathVariable UUID id) {
    return ApiResponse.ok(employeService.listerDocuments(id));
  }

  // "inline" (pas "attachment") : laisse le navigateur prévisualiser PDF/image quand il le peut,
  // tout en restant téléchargeable via son propre bouton de sauvegarde.
  @GetMapping("/{id}/documents/{documentId}/telecharger")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<Resource> telechargerDocument(
      @PathVariable UUID id, @PathVariable UUID documentId) {
    EmployeDocumentTelecharge telecharge = employeService.telechargerDocument(id, documentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(telecharge.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(telecharge.nomOriginal()).build().toString())
        .body(telecharge.ressource());
  }

  @PostMapping("/{id}/photo")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeReponse> televerserPhoto(
      @PathVariable UUID id, @RequestPart MultipartFile photo) {
    UUID televersePar = CurrentUser.id().orElse(null);
    return ApiResponse.ok(
        EmployeReponse.depuis(employeService.televerserPhoto(id, photo, televersePar)));
  }

  @GetMapping("/{id}/photo")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<Resource> recupererPhoto(@PathVariable UUID id) {
    EmployePhotoTelecharge photo = employeService.recupererPhoto(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(photo.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename("photo.jpg").build().toString())
        .body(photo.ressource());
  }

  @DeleteMapping("/{id}/documents/{documentId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> supprimerDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
    employeService.supprimerDocument(id, documentId);
    return ApiResponse.ok();
  }

  @PutMapping("/{id}/documents/{documentId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<EmployeDocumentReponse> remplacerDocument(
      @PathVariable UUID id, @PathVariable UUID documentId, @RequestPart MultipartFile fichier) {
    UUID televersePar = CurrentUser.id().orElse(null);
    return ApiResponse.ok(employeService.remplacerDocument(id, documentId, fichier, televersePar));
  }

  @PostMapping("/{id}/carte/envoyer-email")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> envoyerCarteParEmail(
      @PathVariable UUID id, @Valid @RequestBody CarteEmailRequete requete) {
    employeService.envoyerCarteParEmail(id, requete);
    return ApiResponse.ok();
  }

  @ExceptionHandler(EmployeIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(EmployeIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DepartementIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererDepartementIntrouvable(
      DepartementIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EmployeEmailDejaUtiliseException.class)
  ResponseEntity<ApiResponse<Void>> gererEmailDejaUtilise(EmployeEmailDejaUtiliseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DateFinContratInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererDateFinContratInvalide(
      DateFinContratInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DateFinStageInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererDateFinStageInvalide(DateFinStageInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(FichierInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererFichierInvalide(FichierInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EmployeDocumentIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererDocumentIntrouvable(
      EmployeDocumentIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(PhotoEmployeIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererPhotoIntrouvable(PhotoEmployeIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EmployeSansEmailException.class)
  ResponseEntity<ApiResponse<Void>> gererSansEmail(EmployeSansEmailException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }
}
