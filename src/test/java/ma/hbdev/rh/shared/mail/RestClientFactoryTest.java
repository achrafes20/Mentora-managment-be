package ma.hbdev.rh.shared.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
