package ma.hbdev.rh.shared.mail;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class RestClientFactory {

  private RestClientFactory() {}

  public static RestClient buildWithTimeouts(
      RestClient.Builder builder, Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    if (connectTimeout != null) {
      requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
    }
    if (readTimeout != null) {
      requestFactory.setReadTimeout((int) readTimeout.toMillis());
    }
    RestClient.Builder configuredBuilder = builder.requestFactory(requestFactory);
    return (configuredBuilder != null ? configuredBuilder : builder).build();
  }
}
