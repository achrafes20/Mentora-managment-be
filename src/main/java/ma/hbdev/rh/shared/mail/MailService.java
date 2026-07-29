package ma.hbdev.rh.shared.mail;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class MailService {

  /**
   * Chemin du webhook n8n qui réalise l'envoi SMTP (workflow {@code T0.B3 - Webhook vers SMTP}).
   * Constante partagée : c'est le seul endroit du code qui connaît ce chemin.
   */
  public static final String CHEMIN_WEBHOOK_NOTIFY_EMAIL = "/webhook/notify-email";

  private final RestClient restClient;

  public MailService(@Value("${app.n8n.base-url:http://localhost:5678}") String n8nBaseUrl) {
    this.restClient =
        RestClientFactory.buildWithTimeouts(
            RestClient.builder().baseUrl(urlWebhookNotifyEmail(n8nBaseUrl)),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10));
  }

  /**
   * Construit l'URL complète du webhook d'envoi d'e-mail à partir de l'origine de n8n.
   *
   * <p>Point d'assemblage unique, volontairement : cette URL était auparavant reconstruite dans
   * trois services, à partir de trois propriétés différentes ({@code n8n.webhook.url}, {@code
   * app.n8n.webhook-url}), dont deux attendaient une origine et une l'URL complète. Une seule
   * propriété ({@code app.n8n.base-url}) et une seule concaténation désormais.
   *
   * @param baseUrl origine de n8n, ex. {@code http://n8n:5678} (slash final toléré)
   */
  public static String urlWebhookNotifyEmail(String baseUrl) {
    return baseUrl.replaceAll("/+$", "") + CHEMIN_WEBHOOK_NOTIFY_EMAIL;
  }

  public void sendEmail(String to, String subject, String message) {
    try {
      Map<String, String> body =
          Map.of(
              "to", to,
              "subject", subject,
              "message", message);
      restClient
          .post()
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      log.info("E-mail envoyé avec succès à {} via n8n", to);
    } catch (Exception ex) {
      log.error("Échec de l'envoi d'e-mail à {} via n8n (webhook)", to, ex);
      // Dégradation gracieuse : l'application continue même si le webhook n8n ou Mailpit est hors
      // ligne
    }
  }
}
