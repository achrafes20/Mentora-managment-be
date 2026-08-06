package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.time.Instant;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiosque de pointage — endpoints publics (EF-ATT-02), accessibles sans session JWT (cf. {@code
 * PUBLIC_PATHS} dans {@link ma.hbdev.rh.shared.security.SecurityConfig}).
 *
 * <p>NFR-UX-02 : {@code /scan} exige désormais un jeton d'activation par appareil (en-tête {@value
 * #EN_TETE_JETON_APPAREIL}, émis via {@code /activation/verifier}) plutôt que d'être ouvert sans
 * aucun contrôle — le QR code seul n'authentifiait pas l'appareil qui l'interroge, seulement
 * l'employé qui le présente. La gestion des codes (génération/révocation, Admin ou délégué actif)
 * vit dans {@link KiosqueActivationController}, distinct car authentifié.
 */
@RestController
@RequestMapping("/api/kiosque")
public class KiosqueController {

  private static final String EN_TETE_JETON_APPAREIL = "X-Kiosque-Device-Token";

  private final PointageService pointageService;
  private final KiosqueActivationService activationService;

  public KiosqueController(
      PointageService pointageService, KiosqueActivationService activationService) {
    this.pointageService = pointageService;
    this.activationService = activationService;
  }

  @PostMapping("/scan")
  public ApiResponse<PointageReponse> scanner(
      @RequestHeader(value = EN_TETE_JETON_APPAREIL, required = false) String jetonAppareil,
      @Valid @RequestBody ScanRequete requete) {
    activationService.verifierAppareilActif(jetonAppareil);
    return ApiResponse.ok(PointageReponse.depuis(pointageService.scanner(requete)));
  }

  /**
   * Cet appareil a-t-il déjà une activation valide ? Détermine si {@code /kiosque} montre le prompt
   * de code ou l'écran de scan directement.
   */
  @GetMapping("/activation/statut")
  public ApiResponse<StatutActivationReponse> statutActivation(
      @RequestHeader(value = EN_TETE_JETON_APPAREIL, required = false) String jetonAppareil) {
    return ApiResponse.ok(
        new StatutActivationReponse(activationService.estAppareilActif(jetonAppareil)));
  }

  @PostMapping("/activation/verifier")
  public ApiResponse<JetonAppareilReponse> verifierCode(
      @Valid @RequestBody CodeActivationRequete requete) {
    return ApiResponse.ok(new JetonAppareilReponse(activationService.verifierCode(requete.code())));
  }

  @ExceptionHandler(QrCodeInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererQrInvalide(QrCodeInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DoubleEntreeException.class)
  ResponseEntity<ApiResponse<Void>> gererDoubleEntree(DoubleEntreeException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(AppareilNonActiveException.class)
  ResponseEntity<ApiResponse<Void>> gererAppareilNonActive(AppareilNonActiveException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(CodeActivationInvalideException.class)
  ResponseEntity<ApiResponse<VerrouillageReponse>> gererCodeInvalide(
      CodeActivationInvalideException ex) {
    ApiResponse<VerrouillageReponse> body =
        ex.getVerrouilleJusquA() == null
            ? ApiResponse.error(ex.getMessage())
            : new ApiResponse<>(
                false,
                new VerrouillageReponse(ex.getVerrouilleJusquA()),
                ex.getMessage(),
                Instant.now());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(CodeActivationDejaUtiliseException.class)
  ResponseEntity<ApiResponse<Void>> gererCodeDejaUtilise(CodeActivationDejaUtiliseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(CodeActivationExpireException.class)
  ResponseEntity<ApiResponse<Void>> gererCodeExpire(CodeActivationExpireException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }
}
