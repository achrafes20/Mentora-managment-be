package ma.hbdev.rh.document;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/surveillance")
class SurveillanceController {

  private final SurveillancePlanifieeService surveillancePlanifieeService;
  private final String webhookSecret;

  SurveillanceController(
      SurveillancePlanifieeService surveillancePlanifieeService,
      @Value("${app.internal-webhook-secret:MentoraDevSecret}") String webhookSecret) {
    this.surveillancePlanifieeService = surveillancePlanifieeService;
    this.webhookSecret = webhookSecret;
  }

  @PostMapping("/run")
  ResponseEntity<?> executerSurveillance(@RequestHeader(value = "X-Internal-Webhook-Secret", required = false) String secret) {
    if (secret == null || !secret.equals(webhookSecret)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Secret invalide"));
    }
    
    surveillancePlanifieeService.executerSurveillance();
    return ResponseEntity.ok(Map.of("status", "success"));
  }
}
