package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * EF-ATT-17 : gestion des QR codes de site (Admin, ou délégué actif) — session JWT requise,
 * contrairement à {@link KiosqueController}. Un QR de site, une fois affiché/imprimé, est scanné
 * par le téléphone personnel d'un employé déjà appairé pour pointer (cf. /api/kiosque/scan-site).
 */
@RestController
@RequestMapping("/api/kiosque/sites")
@PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
class SiteQrCodeController {

  private final SiteQrCodeService service;

  SiteQrCodeController(SiteQrCodeService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<SiteQrCodeReponse> generer(@Valid @RequestBody SiteQrCodeRequete requete) {
    return ApiResponse.ok(SiteQrCodeReponse.depuis(service.generer(requete.libelle())));
  }

  @GetMapping
  ApiResponse<List<SiteQrCodeReponse>> lister() {
    return ApiResponse.ok(service.lister().stream().map(SiteQrCodeReponse::depuis).toList());
  }

  @PostMapping("/{id}/desactiver")
  ApiResponse<Void> desactiver(@PathVariable UUID id) {
    service.desactiver(id);
    return ApiResponse.ok();
  }

  @ExceptionHandler(SiteQrCodeIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(SiteQrCodeIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
