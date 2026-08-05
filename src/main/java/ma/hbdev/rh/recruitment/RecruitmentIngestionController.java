package ma.hbdev.rh.recruitment;

import ma.hbdev.rh.shared.security.InternalWebhookGuard;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint appelé uniquement par le workflow n8n d'ingestion IMAP (jamais par un utilisateur
 * connecté) — {@code permitAll()} dans {@code SecurityConfig}, protégé par {@link
 * InternalWebhookGuard} (secret partagé en en-tête) plutôt que par une session JWT.
 *
 * <p>L'archivage des candidatures expirées (EF-REC-12) ne passe plus par ici — voir {@code
 * CandidatureService#archivageQuotidien} (cron backend) et {@code
 * CandidatureController#archiverExpirees} (déclenchement manuel Admin).
 */
@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentIngestionController {

  private static final String EN_TETE_SECRET = "X-Internal-Webhook-Secret";

  private final CandidatureIngestionService candidatureIngestionService;
  private final InternalWebhookGuard internalWebhookGuard;

  public RecruitmentIngestionController(
      CandidatureIngestionService candidatureIngestionService,
      InternalWebhookGuard internalWebhookGuard) {
    this.candidatureIngestionService = candidatureIngestionService;
    this.internalWebhookGuard = internalWebhookGuard;
  }

  // EF-REC-02/03 : le workflow n8n IMAP transfère l'e-mail brut tel quel (transport only,
  // ai-instructions.md règle 7) — toute normalisation reste ici.
  @PostMapping("/ingest")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CandidatureReponse> ingerer(
      @RequestHeader(value = EN_TETE_SECRET, required = false) String secret,
      @RequestParam String emailExpediteur,
      @RequestParam(required = false) String nomExpediteur,
      @RequestParam(required = false) String sujet,
      @RequestParam(required = false) String corps,
      @RequestParam(required = false) String referenceSourceImport,
      @RequestPart(required = false) MultipartFile cv) {
    internalWebhookGuard.verifier(secret);
    Candidature candidature =
        candidatureIngestionService.ingerer(
            emailExpediteur, nomExpediteur, sujet, corps, referenceSourceImport, cv);
    return ApiResponse.ok(CandidatureReponse.depuis(candidature));
  }
}
