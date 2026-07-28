package ma.hbdev.rh.shared.ai;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Seul point du code qui sait que le vendor IA est OpenRouter (ai-instructions.md règle 6). Si
 * {@code app.ai.openrouter.api-key} est vide (pas de clé en dev/CI), le bean dégradé est enregistré
 * à la place — l'application démarre normalement (EF-REC-05).
 *
 * <p>OpenRouter remplace Gemini depuis le 2026-07-28 (palier gratuit Gemini bloqué à un quota de 0,
 * facturation impossible à activer côté HB Développement) — modèle par défaut {@code
 * openai/gpt-oss-20b:free} : gratuit sans carte, 50 requêtes/jour.
 */
@Configuration
class CvAnalysisConfig {

  @Bean
  CvAnalysisProvider cvAnalysisProvider(
      @Value("${app.ai.openrouter.api-key:}") String apiKey,
      @Value("${app.ai.openrouter.model:openai/gpt-oss-20b:free}") String model) {
    if (apiKey == null || apiKey.isBlank()) {
      return new NoopCvAnalysisProvider();
    }
    // Sans timeout explicite, une réponse lente/bloquée pendrait indéfiniment l'ingestion/la
    // relance d'analyse — même bug que celui trouvé et corrigé dans MailService. 30s mesuré trop
    // court en conditions réelles : le modèle gratuit ("reasoning", ~50% de tokens de
    // raisonnement caché avant la réponse) a pris 22,5s sur un prompt court — un vrai CV plus
    // long dépasse régulièrement les 30s, provoquant un "échec" qui n'en est pas un.
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(60));
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
    return new OpenRouterCvAnalysisProvider(apiKey, model, restClient);
  }
}
