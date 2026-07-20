package ma.hbdev.rh.shared.mattermost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MattermostApiClientTest {

  private HttpServer server;

  @AfterEach
  void arreterServeur() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void envoieUnMessagePriveAvecLeMappingMattermostDuCompte() throws IOException {
    AtomicInteger directCalls = new AtomicInteger();
    AtomicInteger postCalls = new AtomicInteger();
    demarrerServeur(
        exchange -> {
          if ("/api/v4/channels/direct".equals(exchange.getRequestURI().getPath())) {
            directCalls.incrementAndGet();
            repondreJson(exchange, 201, "{\"id\":\"channel-1\"}");
          } else if ("/api/v4/posts".equals(exchange.getRequestURI().getPath())) {
            postCalls.incrementAndGet();
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("channel-1").contains("Message prive");
            repondreJson(exchange, 201, "{\"id\":\"post-1\"}");
          } else {
            repondreJson(exchange, 404, "{}");
          }
        });
    UUID destinataireId = UUID.randomUUID();
    UserRepository repository = mock(UserRepository.class);
    when(repository.findById(destinataireId))
        .thenReturn(java.util.Optional.of(utilisateur("manager@test.ma", "mm-user-1")));

    ResultatMattermost resultat =
        client(repository, true).envoyerMessagePrive(destinataireId, "Message prive");

    assertThat(resultat.reussi()).isTrue();
    assertThat(directCalls).hasValue(1);
    assertThat(postCalls).hasValue(1);
  }

  @Test
  void retrouveLeCompteMattermostParEmailQuandLeMappingInterneEstAbsent() throws IOException {
    AtomicInteger emailLookupCalls = new AtomicInteger();
    demarrerServeur(
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          if (path.startsWith("/api/v4/users/email/")) {
            emailLookupCalls.incrementAndGet();
            repondreJson(exchange, 200, "{\"id\":\"mm-user-email\"}");
          } else if ("/api/v4/channels/direct".equals(path)) {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("bot-user").contains("mm-user-email");
            repondreJson(exchange, 201, "{\"id\":\"channel-1\"}");
          } else if ("/api/v4/posts".equals(path)) {
            repondreJson(exchange, 201, "{\"id\":\"post-1\"}");
          } else {
            repondreJson(exchange, 404, "{}");
          }
        });
    UUID destinataireId = UUID.randomUUID();
    UserRepository repository = mock(UserRepository.class);
    when(repository.findById(destinataireId))
        .thenReturn(java.util.Optional.of(utilisateur("manager@test.ma", null)));

    ResultatMattermost resultat =
        client(repository, true).envoyerMessagePrive(destinataireId, "Message prive");

    assertThat(resultat.reussi()).isTrue();
    assertThat(emailLookupCalls).hasValue(1);
  }

  @Test
  void echoueSansAppelerLaBaseQuandLaConfigurationEstIncomplete() {
    UserRepository repository = mock(UserRepository.class);
    MattermostApiClient client = new MattermostApiClient(repository, "", "", "", true, 1000, 1000);

    ResultatMattermost resultat = client.envoyerMessagePrive(UUID.randomUUID(), "Message prive");

    assertThat(resultat.reussi()).isFalse();
    assertThat(resultat.erreur()).contains("Configuration Mattermost");
    verifyNoInteractions(repository);
  }

  private MattermostApiClient client(UserRepository repository, boolean lookupByEmail) {
    return new MattermostApiClient(
        repository,
        "http://localhost:" + server.getAddress().getPort(),
        "token",
        "bot-user",
        lookupByEmail,
        1000,
        1000);
  }

  private User utilisateur(String email, String mattermostUserId) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(email);
    user.setMotDePasseHash("hash");
    user.setRole(RoleUtilisateur.manager);
    user.setNom("Manager");
    user.setPrenom("Test");
    user.setMattermostUserId(mattermostUserId);
    return user;
  }

  private void demarrerServeur(ExchangeHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", handler::handle);
    server.start();
  }

  private void repondreJson(HttpExchange exchange, int status, String json) throws IOException {
    byte[] response = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, response.length);
    try (OutputStream output = exchange.getResponseBody()) {
      output.write(response);
    }
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }
}
