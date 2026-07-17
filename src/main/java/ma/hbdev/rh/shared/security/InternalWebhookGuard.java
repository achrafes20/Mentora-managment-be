package ma.hbdev.rh.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Auth machine-à-machine par secret partagé, pour les endpoints appelés par n8n (jamais par un
 * utilisateur connecté) — ex. ingestion de candidature (EF-REC-02), cron d'archivage (EF-REC-12).
 * Ces chemins restent {@code permitAll()} dans {@code SecurityConfig} (comme {@code
 * /api/kiosque/**}) mais sont protégés ici par un en-tête {@code X-Internal-Webhook-Secret} plutôt
 * que d'être ouverts sans contrôle. Pensé pour être réutilisé par les futurs pings cron n8n de
 * T4.A1 (surveillance fin de stage/CDD).
 */
@Component
public class InternalWebhookGuard {

  private final String secretAttendu;

  public InternalWebhookGuard(
      @Value("${app.security.internal-webhook-secret:}") String secretAttendu) {
    this.secretAttendu = secretAttendu;
  }

  public void verifier(String secretRecu) {
    if (secretAttendu == null
        || secretAttendu.isBlank()
        || secretRecu == null
        || !secretAttendu.equals(secretRecu)) {
      throw new AccessDeniedException("Secret webhook interne invalide ou absent");
    }
  }
}
