package ma.hbdev.rh.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.audit.AuditConsultationService;
import ma.hbdev.rh.shared.audit.JournalAuditReponse;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EF-CFG-04/06 — consultation du journal d'audit (lecture seule, Admin uniquement, jamais délégable
 * EF-AUTH-12).
 *
 * <p>Préfixe : /api/audit
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit", description = "Consultation du journal d'audit (lecture seule, Admin)")
public class AuditController {

  private final AuditConsultationService auditConsultationService;
  private final AuditExportService auditExportService;

  @GetMapping
  @Operation(summary = "Rechercher dans le journal d'audit (module/utilisateur/période/texte)")
  public ApiResponse<PagedResponse<JournalAuditReponse>> rechercher(
      @RequestParam(required = false) ModuleAudit module,
      @RequestParam(required = false) UUID utilisateurId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate debut,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
      @RequestParam(required = false) String recherche,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        PagedResponse.of(
            auditConsultationService.rechercher(
                module, utilisateurId, debut, fin, recherche, page, size)));
  }

  // EF-CFG-05 : export — chemin statique, aucune ambiguïté (ce contrôleur n'a pas de "/{id}").
  @GetMapping("/export")
  @Operation(summary = "Exporter le journal d'audit filtré (Excel ou PDF)")
  public ResponseEntity<byte[]> exporter(
      @RequestParam FormatExport format,
      @RequestParam(required = false) ModuleAudit module,
      @RequestParam(required = false) UUID utilisateurId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate debut,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
      @RequestParam(required = false) String recherche) {
    byte[] contenu =
        auditExportService.exporter(module, utilisateurId, debut, fin, recherche, format);
    String nomFichier = "audit_" + LocalDate.now() + format.extension();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(format.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nomFichier).build().toString())
        .body(contenu);
  }
}
