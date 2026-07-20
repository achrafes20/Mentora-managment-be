package ma.hbdev.rh.shared.mattermost;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
class WebhookMattermostClient implements MattermostClient {

  private final RestClient restClient;
  private final String webhookUrl;

  WebhookMattermostClient(
      RestClient.Builder restClientBuilder,
      @Value("${app.mattermost.webhook-url:}") String webhookUrl,
      @Value("${app.mattermost.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${app.mattermost.read-timeout-ms:5000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    this.webhookUrl = webhookUrl;
  }

  @Override
  public ResultatMattermost envoyer(String message) {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      return ResultatMattermost.echec("Webhook Mattermost non configure");
    }
    try {
      restClient.post().uri(webhookUrl).body(Map.of("text", message)).retrieve().toBodilessEntity();
      return ResultatMattermost.succes();
    } catch (RestClientException | IllegalArgumentException exception) {
      log.warn("Echec d'envoi du webhook Mattermost: {}", exception.getMessage());
      return ResultatMattermost.echec(messageErreur(exception));
    }
  }

  private String messageErreur(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
