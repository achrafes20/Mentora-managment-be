package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Historique des pointages (EF-ATT-05) et correction manuelle (EF-ATT-06 — Could). */
@RestController
@RequestMapping("/api/pointages")
public class PointageController {

  private final PointageService pointageService;
  private final QrCodeService qrCodeService;

  public PointageController(PointageService pointageService, QrCodeService qrCodeService) {
    this.pointageService = pointageService;
    this.qrCodeService = qrCodeService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<PointageReponse>> lister(Pageable pageable) {
    var page = pointageService.lister(pageable);
    return ApiResponse.ok(PagedResponse.of(page.map(PointageReponse::depuis)));
  }

  @GetMapping("/employe/{employeId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<PointageReponse>> listerParEmploye(
      @PathVariable UUID employeId, Pageable pageable) {
    var page = pointageService.listerParEmploye(employeId, pageable);
    return ApiResponse.ok(PagedResponse.of(page.map(PointageReponse::depuis)));
  }

  @PostMapping("/{id}/corriger")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PointageReponse> corrigerManuellement(
      @PathVariable UUID id, @Valid @RequestBody CorrectionPointageRequete requete) {
    return ApiResponse.ok(
        PointageReponse.depuis(pointageService.corrigerManuellement(id, requete)));
  }

  /** Génère ou régénère le QR code actif d'un employé. */
  @PostMapping("/qr-code/generer/{employeId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<QrCodeReponse> genererQrCode(@PathVariable UUID employeId) {
    return ApiResponse.ok(QrCodeReponse.depuis(qrCodeService.generer(employeId)));
  }

  /** Retourne le QR code actif d'un employé (pour l'afficher sur la fiche). */
  @GetMapping("/qr-code/{employeId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<QrCodeReponse> qrCodeActif(@PathVariable UUID employeId) {
    return qrCodeService
        .trouverActifDe(employeId)
        .map(qr -> ApiResponse.ok(QrCodeReponse.depuis(qr)))
        .orElse(ApiResponse.ok(null));
  }

  @ExceptionHandler(PointageIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(PointageIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
