package ma.hbdev.rh.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * EF-CFG-01/02 — identité de l'entreprise. Réservé Admin (jamais délégable, EF-AUTH-12).
 *
 * <p>Préfixe : /api/config/identite-entreprise
 */
@RestController
@RequestMapping("/api/config/identite-entreprise")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Configuration", description = "Identité de l'entreprise (Admin uniquement)")
public class IdentiteEntrepriseController {

  private final IdentiteEntrepriseService service;

  @GetMapping
  @Operation(summary = "Consulter l'identité de l'entreprise")
  public ApiResponse<IdentiteEntrepriseReponse> obtenir() {
    return ApiResponse.ok(service.obtenir());
  }

  @PutMapping
  @Operation(summary = "Modifier l'identité de l'entreprise")
  public ApiResponse<IdentiteEntrepriseReponse> modifier(
      @RequestBody IdentiteEntrepriseRequete requete) {
    return ApiResponse.ok(service.modifier(requete));
  }

  @PostMapping("/logo")
  @Operation(summary = "Téléverser (ou remplacer) le logo de l'entreprise")
  public ApiResponse<IdentiteEntrepriseReponse> televerserLogo(@RequestPart MultipartFile logo) {
    return ApiResponse.ok(service.televerserLogo(logo, CurrentUser.id().orElse(null)));
  }

  @GetMapping("/logo")
  @Operation(summary = "Télécharger le logo de l'entreprise")
  public ResponseEntity<Resource> recupererLogo() {
    LogoEntrepriseTelecharge logo = service.recupererLogo();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(logo.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename("logo").build().toString())
        .body(logo.ressource());
  }

  @PostMapping("/signature")
  @Operation(summary = "Téléverser (ou remplacer) la signature/cachet de l'entreprise")
  public ApiResponse<IdentiteEntrepriseReponse> televerserSignature(
      @RequestPart MultipartFile signature) {
    return ApiResponse.ok(service.televerserSignature(signature, CurrentUser.id().orElse(null)));
  }

  @GetMapping("/signature")
  @Operation(summary = "Télécharger la signature/cachet de l'entreprise")
  public ResponseEntity<Resource> recupererSignature() {
    SignatureEntrepriseTelecharge signature = service.recupererSignature();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(signature.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename("signature").build().toString())
        .body(signature.ressource());
  }

  @ExceptionHandler(LogoEntrepriseIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererLogoIntrouvable(LogoEntrepriseIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(SignatureEntrepriseIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererSignatureIntrouvable(
      SignatureEntrepriseIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
