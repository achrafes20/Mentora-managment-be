package ma.hbdev.rh.shared.mattermost;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
class MattermostApiClient implements MattermostClient {

  private final UserRepository userRepository;
  private final RestClient restClient;
  private final String baseUrl;
  private final String botToken;
  private final String botUserId;
  private final boolean lookupUserByEmail;

  MattermostApiClient(
      UserRepository userRepository,
      @Value("${app.mattermost.base-url:}") String baseUrl,
      @Value("${app.mattermost.bot-token:}") String botToken,
      @Value("${app.mattermost.bot-user-id:}") String botUserId,
      @Value("${app.mattermost.lookup-user-by-email:true}") boolean lookupUserByEmail,
      @Value("${app.mattermost.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${app.mattermost.read-timeout-ms:5000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    this.userRepository = userRepository;
    this.baseUrl = nettoyer(baseUrl);
    this.botToken = nettoyer(botToken);
    this.botUserId = nettoyer(botUserId);
    this.lookupUserByEmail = lookupUserByEmail;
    this.restClient =
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(this.baseUrl == null ? "" : this.baseUrl)
            .defaultHeader(
                HttpHeaders.AUTHORIZATION, "Bearer " + (this.botToken == null ? "" : this.botToken))
            .build();
  }

  @Override
  public ResultatMattermost envoyerMessagePrive(UUID destinataireId, String message) {
    if (!configurationComplete()) {
      return ResultatMattermost.echec("Configuration Mattermost privee incomplete");
    }

    User destinataire = userRepository.findById(destinataireId).orElse(null);
    if (destinataire == null) {
      return ResultatMattermost.echec("Utilisateur destinataire introuvable");
    }
    if (!destinataire.isActive()) {
      return ResultatMattermost.echec("Utilisateur destinataire inactif");
    }

    try {
      String mattermostUserId = resoudreMattermostUserId(destinataire);
      if (mattermostUserId == null) {
        return ResultatMattermost.echec("Identifiant Mattermost destinataire introuvable");
      }
      MattermostChannel channel =
          restClient
              .post()
              .uri("/api/v4/channels/direct")
              .body(List.of(botUserId, mattermostUserId))
              .retrieve()
              .body(MattermostChannel.class);
      if (channel == null || channel.id() == null || channel.id().isBlank()) {
        return ResultatMattermost.echec("Canal direct Mattermost introuvable");
      }
      restClient
          .post()
          .uri("/api/v4/posts")
          .body(Map.of("channel_id", channel.id(), "message", message))
          .retrieve()
          .toBodilessEntity();
      return ResultatMattermost.succes();
    } catch (RestClientException | IllegalArgumentException exception) {
      log.warn("Echec d'envoi Mattermost prive: {}", exception.getMessage());
      return ResultatMattermost.echec(messageErreur(exception));
    }
  }

  private String resoudreMattermostUserId(User destinataire) {
    String mapping = nettoyer(destinataire.getMattermostUserId());
    if (mapping != null) {
      return mapping;
    }
    if (!lookupUserByEmail) {
      return null;
    }
    MattermostUser user =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder.path("/api/v4/users/email/{email}").build(destinataire.getEmail()))
            .retrieve()
            .body(MattermostUser.class);
    return user == null ? null : nettoyer(user.id());
  }

  private boolean configurationComplete() {
    return baseUrl != null && botToken != null && botUserId != null;
  }

  private String nettoyer(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    return valeur.trim();
  }

  private String messageErreur(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  private record MattermostUser(String id) {}

  private record MattermostChannel(String id) {}
}
