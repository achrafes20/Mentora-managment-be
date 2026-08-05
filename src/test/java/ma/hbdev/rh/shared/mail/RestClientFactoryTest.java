package ma.hbdev.rh.shared.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientFactoryTest {

  @Test
  void shouldConfigureConnectAndReadTimeouts() {
    RestClient.Builder builder = mock(RestClient.Builder.class);
    RestClient restClient = mock(RestClient.class);
    when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
    when(builder.build()).thenReturn(restClient);

    RestClient builtClient =
        RestClientFactory.buildWithTimeouts(builder, Duration.ofSeconds(3), Duration.ofSeconds(7));

    assertSame(restClient, builtClient);

    ArgumentCaptor<SimpleClientHttpRequestFactory> requestFactoryCaptor =
        ArgumentCaptor.forClass(SimpleClientHttpRequestFactory.class);
    verify(builder).requestFactory(requestFactoryCaptor.capture());

    SimpleClientHttpRequestFactory factory = requestFactoryCaptor.getValue();
    assertEquals(3000, getTimeout(factory, "connectTimeout"));
    assertEquals(7000, getTimeout(factory, "readTimeout"));
  }

  /**
   * Régression : {@link #shouldConfigureConnectAndReadTimeouts} ne prouve que la configuration, pas
   * qu'elle produit un vrai effet. Ici un serveur TCP réel accepte la connexion (le connect timeout
   * n'a donc pas lieu d'intervenir) mais ne répond jamais : seul un timeout de LECTURE
   * effectivement appliqué peut faire échouer l'appel, et rapidement (300ms), pas après le connect
   * timeout (2s).
   */
  @Test
  void shouldTimeoutWhenServerAcceptsButNeverResponds() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      int port = serverSocket.getLocalPort();
      Thread serveurMuet =
          new Thread(
              () -> {
                try (Socket ignored = serverSocket.accept()) {
                  Thread.sleep(5000);
                } catch (Exception ignored) {
                  // Le test se termine avant : rien à faire.
                }
              });
      serveurMuet.setDaemon(true);
      serveurMuet.start();

      RestClient client =
          RestClientFactory.buildWithTimeouts(
              RestClient.builder().baseUrl("http://localhost:" + port),
              Duration.ofSeconds(2),
              Duration.ofMillis(300));

      long debut = System.currentTimeMillis();
      assertThrows(
          ResourceAccessException.class, () -> client.post().retrieve().toBodilessEntity());
      long dureeMs = System.currentTimeMillis() - debut;

      assertTrue(
          dureeMs < 2000,
          "Le timeout de lecture (300ms) doit se déclencher bien avant le connect timeout (2s), a"
              + " duré "
              + dureeMs
              + "ms");
    }
  }

  /** Régression : connexion refusée (rien n'écoute) — doit échouer, pas rester bloqué. */
  @Test
  void shouldFailOnConnectionRefused() throws Exception {
    int portFerme;
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      portFerme = serverSocket.getLocalPort();
    } // fermé aussitôt : personne n'écoute plus dessus -> connexion refusée garantie

    RestClient client =
        RestClientFactory.buildWithTimeouts(
            RestClient.builder().baseUrl("http://localhost:" + portFerme),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2));

    assertThrows(ResourceAccessException.class, () -> client.post().retrieve().toBodilessEntity());
  }

  private int getTimeout(SimpleClientHttpRequestFactory factory, String fieldName) {
    try {
      Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (int) field.get(factory);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
