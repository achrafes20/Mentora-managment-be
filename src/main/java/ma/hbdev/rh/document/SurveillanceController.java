package ma.hbdev.rh.document;

import java.util.Map;
import ma.hbdev.rh.shared.security.InternalWebhookGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Déclenchement manuel du balayage de surveillance des fins de contrat (EF-DOC-12/13/14).
 *
 * <p>Le balayage tourne normalement tout seul, via le {@code @Scheduled} de {@link
 * SurveillancePlanifieeService}. Cet endpoint reste utile pour rejouer un balayage à la demande
 * (rattrapage après une indisponibilité, vérification en recette) sans attendre le cron.
 *
 * <p>Protégé par le même secret partagé que les autres endpoints machine-à-machine, via {@link
 * InternalWebhookGuard} — le chemin est {@code permitAll()} dans {@code SecurityConfig}, c'est ce
 * garde qui l'authentifie réellement.
 */
@RestController
@RequestMapping("/api/internal/surveillance")
class SurveillanceController {

  private final SurveillancePlanifieeService surveillancePlanifieeService;
  private final InternalWebhookGuard internalWebhookGuard;

  SurveillanceController(
      SurveillancePlanifieeService surveillancePlanifieeService,
      InternalWebhookGuard internalWebhookGuard) {
    this.surveillancePlanifieeService = surveillancePlanifieeService;
    this.internalWebhookGuard = internalWebhookGuard;
  }

  @PostMapping("/run")
  ResponseEntity<?> executerSurveillance(
      @RequestHeader(value = "X-Internal-Webhook-Secret", required = false) String secret) {
    try {
      internalWebhookGuard.verifier(secret);
    } catch (AccessDeniedException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("error", "Secret invalide"));
    }

    surveillancePlanifieeService.executerSurveillance();
    return ResponseEntity.ok(Map.of("status", "success"));
  }
}
