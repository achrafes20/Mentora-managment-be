package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiosque de pointage — endpoint public (EF-ATT-02). Accessible sans JWT car le QR code fait office
 * d'authentification implicite. Sécurisé via {@link ma.hbdev.rh.shared.security.SecurityConfig}
 * ({@code /api/kiosque/**} en permitAll).
 */
@RestController
@RequestMapping("/api/kiosque")
public class KiosqueController {

  private final PointageService pointageService;

  public KiosqueController(PointageService pointageService) {
    this.pointageService = pointageService;
  }

  @PostMapping("/scan")
  public ApiResponse<PointageReponse> scanner(@Valid @RequestBody ScanRequete requete) {
    return ApiResponse.ok(PointageReponse.depuis(pointageService.scanner(requete)));
  }

  @ExceptionHandler(QrCodeInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererQrInvalide(QrCodeInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DoubleEntreeException.class)
  ResponseEntity<ApiResponse<Void>> gererDoubleEntree(DoubleEntreeException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }
}
