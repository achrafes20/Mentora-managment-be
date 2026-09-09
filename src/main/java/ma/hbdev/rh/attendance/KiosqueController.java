package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final SiteQrCodeService siteQrCodeService;

  public KiosqueController(
      PointageService pointageService,
      KiosqueActivationService activationService,
      SiteQrCodeService siteQrCodeService) {
    this.pointageService = pointageService;
    this.activationService = activationService;
    this.siteQrCodeService = siteQrCodeService;
  }

  @PostMapping("/scan")
  public ApiResponse<PointageReponse> scanner(
      @RequestHeader(value = EN_TETE_JETON_APPAREIL, required = false) String jetonAppareil,
      @Valid @RequestBody ScanRequete requete) {
    activationService.verifierAppareilActif(jetonAppareil);
    return ApiResponse.ok(PointageReponse.depuis(pointageService.scanner(requete)));
  }

  /**
   * EF-ATT-17 : pointage depuis le téléphone personnel de l'employé — deux preuves distinctes,
   * combinées : l'identité vient de l'appairage de l'appareil (cf.
   * KiosqueActivationController#genererCodePersonnel), la présence physique au lieu vient du QR de
   * site scanné ({@code valeurQrSite}, affiché/imprimé sur place, cf. SiteQrCodeController) — pas
   * du QR badge de l'employé, qui n'intervient pas dans ce flux.
   */
  @PostMapping("/scan-personnel")
  public ApiResponse<PointageReponse> scannerPersonnel(
      @RequestHeader(value = EN_TETE_JETON_APPAREIL, required = false) String jetonAppareil,
      @Valid @RequestBody ScanPersonnelRequete requete) {
    UUID employeId = activationService.employeAppareilPersonnel(jetonAppareil);
    siteQrCodeService
        .resoudreParValeur(requete.valeurQrSite())
        .orElseThrow(SiteQrInvalideException::new);
    return ApiResponse.ok(
        PointageReponse.depuis(pointageService.scannerPersonnel(employeId, requete.typeScan())));
  }

  /**
   * EF-ATT-17 : historique perso paginé affiché sur /pointage-mobile — pointages de l'employé
   * propriétaire de l'appareil, pour qu'il vérifie qu'il n'a pas oublié de pointer.
   */
  private static final int TAILLE_MES_POINTAGES_MAX = 50;

  @GetMapping("/mes-pointages")
  public ApiResponse<PagedResponse<PointageReponse>> mesPointages(
      @RequestHeader(value = EN_TETE_JETON_APPAREIL, required = false) String jetonAppareil,
      @RequestParam(required = false) TypeScanPointage type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int taille) {
    UUID employeId = activationService.employeAppareilPersonnel(jetonAppareil);
    int limite = Math.min(Math.max(taille, 1), TAILLE_MES_POINTAGES_MAX);
    Pageable pageable = PageRequest.of(Math.max(page, 0), limite);
    return ApiResponse.ok(
        PagedResponse.of(pointageService.pointagesRecents(employeId, pageable, type)));
  }

  /**
   * EF-ATT-19 : révocation en self-service depuis le lien reçu par e-mail — aucune session/en-tête
   * d'appareil requis, le jeton lui-même est la preuve d'intention.
   */
  @PostMapping("/revoquer-perte")
  public ApiResponse<Void> revoquerParJeton(@Valid @RequestBody RevocationParJetonRequete requete) {
    activationService.revoquerParJeton(requete.jeton());
    return ApiResponse.ok();
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

  @ExceptionHandler(SiteQrInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererSiteQrInvalide(SiteQrInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  // EF-ATT-19 : jeton de révocation inconnu/déjà utilisé — message générique, pas de distinction
  // avec "jamais existé" pour ne rien révéler sur la validité passée du lien.
  @ExceptionHandler(KiosqueActivationIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererJetonRevocationInvalide(
      KiosqueActivationIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Lien de révocation invalide ou déjà utilisé."));
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
