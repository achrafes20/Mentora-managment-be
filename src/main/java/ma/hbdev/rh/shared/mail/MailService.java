package ma.hbdev.rh.shared.mail;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class MailService {

  private final RestClient restClient;

  public MailService(
      @Value("${app.n8n.webhook-url:http://n8n:5678/webhook/notify-email}") String webhookUrl) {
    // Sans timeout explicite, un webhook n8n bloqué (ou juste pas encore démarré) fait pendre
    // indéfiniment l'appel HTTP — le try/catch ci-dessous ne protège que contre une exception, pas
    // contre un appel qui ne revient jamais. Bug réel trouvé en testant EF-REC-14 en conditions
    // réelles : la transaction restait "idle in transaction" en base, bloquant toute tentative
    // suivante sur la même candidature.
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    this.restClient =
        RestClient.builder().baseUrl(webhookUrl).requestFactory(requestFactory).build();
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
