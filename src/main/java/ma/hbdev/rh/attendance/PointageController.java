package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  public ApiResponse<PagedResponse<PointageReponse>> lister(
      @RequestParam(required = false) UUID employeId,
      @RequestParam(required = false) TypeScanPointage typeScan,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate debut,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
      Pageable pageable) {
    var page = pointageService.lister(employeId, typeScan, debut, fin, pageable);
    return ApiResponse.ok(PagedResponse.of(page.map(PointageReponse::depuis)));
  }

  @GetMapping("/employe/{employeId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<PointageReponse>> listerParEmploye(
      @PathVariable UUID employeId, Pageable pageable) {
    var page = pointageService.listerParEmploye(employeId, pageable);
    return ApiResponse.ok(PagedResponse.of(page.map(PointageReponse::depuis)));
  }

  /** Tableau de bord Présence : taux de couverture, anomalies récurrentes, répartition par type. */
  @GetMapping("/dashboard")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PresenceDashboardReponse> tableauDeBord() {
    return ApiResponse.ok(pointageService.tableauDeBord());
  }

  // EF-EXP-02 : chemin statique "/export" — pas de conflit avec "/employe/{employeId}" ni
  // "/qr-code/{employeId}" (préfixes distincts), ni avec "/{id}/corriger" (profondeur différente).
  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<byte[]> exporter(
      @RequestParam FormatExport format,
      @RequestParam(required = false) UUID employeId,
      @RequestParam LocalDate debut,
      @RequestParam LocalDate fin) {
    byte[] contenu = pointageService.exporter(format, employeId, debut, fin);
    String nomFichier = "presence_" + debut + "_" + fin + format.extension();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(format.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nomFichier).build().toString())
        .body(contenu);
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
