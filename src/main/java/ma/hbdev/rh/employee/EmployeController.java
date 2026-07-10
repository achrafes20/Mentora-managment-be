package ma.hbdev.rh.employee;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.file.FichierInvalideException;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * EF-EMP (fiche employé). RBAC réel (Admin/Manager selon EF) branché à T1.A1 — endpoints ouverts en
 * attendant (voir SecurityConfig, stopgap retiré à T1.C1).
 */
@RestController
@RequestMapping("/api/employes")
public class EmployeController {

  private final EmployeService employeService;

  public EmployeController(EmployeService employeService) {
    this.employeService = employeService;
  }

  @GetMapping
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

  @GetMapping("/{id}")
  public ApiResponse<EmployeReponse> detail(@PathVariable UUID id) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.trouver(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<EmployeReponse> creer(@Valid @RequestBody EmployeRequete requete) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.creer(requete)));
  }

  @PutMapping("/{id}")
  public ApiResponse<EmployeReponse> modifier(
      @PathVariable UUID id, @Valid @RequestBody EmployeModificationRequete requete) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.modifier(id, requete)));
  }

  @PostMapping("/{id}/transferer")
  public ApiResponse<EmployeReponse> transferer(
      @PathVariable UUID id, @Valid @RequestBody TransfertRequete requete) {
    return ApiResponse.ok(EmployeReponse.depuis(employeService.transferer(id, requete, null)));
  }

  @GetMapping("/{id}/transferts")
  public ApiResponse<List<EmployeTransfertReponse>> historiqueTransferts(@PathVariable UUID id) {
    return ApiResponse.ok(
        employeService.historiqueTransferts(id).stream()
            .map(EmployeTransfertReponse::depuis)
            .toList());
  }

  @PostMapping("/{id}/desactiver")
  public ApiResponse<Void> desactiver(
      @PathVariable UUID id, @Valid @RequestBody DesactivationRequete requete) {
    employeService.desactiver(id, requete);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/documents")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<EmployeDocumentReponse> attacherDocument(
      @PathVariable UUID id,
      @RequestPart MultipartFile fichier,
      @RequestPart(required = false) String typeDocument) {
    return ApiResponse.ok(employeService.attacherDocument(id, fichier, typeDocument, null));
  }

  @GetMapping("/{id}/documents")
  public ApiResponse<List<EmployeDocumentReponse>> listerDocuments(@PathVariable UUID id) {
    return ApiResponse.ok(employeService.listerDocuments(id));
  }

  // "inline" (pas "attachment") : laisse le navigateur prévisualiser PDF/image quand il le peut,
  // tout en restant téléchargeable via son propre bouton de sauvegarde.
  @GetMapping("/{id}/documents/{documentId}/telecharger")
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

  @ExceptionHandler(FichierInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererFichierInvalide(FichierInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EmployeDocumentIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererDocumentIntrouvable(
      EmployeDocumentIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
