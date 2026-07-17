package ma.hbdev.rh.shared.ai;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Seul point du code qui sait que le vendor IA est Gemini (ai-instructions.md règle 6). Si {@code
 * app.ai.gemini.api-key} est vide (pas de clé en dev/CI), le bean dégradé est enregistré à la place
 * — l'application démarre normalement (EF-REC-05).
 */
@Configuration
class CvAnalysisConfig {

  @Bean
  CvAnalysisProvider cvAnalysisProvider(
      @Value("${app.ai.gemini.api-key:}") String apiKey,
      @Value("${app.ai.gemini.model:gemini-2.0-flash}") String model) {
    if (apiKey == null || apiKey.isBlank()) {
      return new NoopCvAnalysisProvider();
    }
    // Sans timeout explicite, une réponse Gemini lente/bloquée pendrait indéfiniment l'ingestion/
    // la relance d'analyse — même bug que celui trouvé et corrigé dans MailService.
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(30));
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new GeminiCvAnalysisProvider(apiKey, model, restClient);
  }
}
