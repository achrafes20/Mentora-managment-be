package ma.hbdev.rh.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Jamais de vraie clé Gemini en test/CI (DoD, ai-instructions.md) — {@code RestClient} est
 * intercepté par {@link MockRestServiceServer}, aucun appel réseau réel.
 */
class GeminiCvAnalysisProviderTest {

  private static final String CORPS_REPONSE_VALIDE =
      """
      {
        "candidates": [
          {
            "content": {
              "parts": [
                {
                  "text": "{\\"prenom\\":\\"Amine\\",\\"nom\\":\\"Bennani\\",\\"email\\":\\"amine@test.ma\\",\\"telephone\\":null,\\"intitulePoste\\":\\"Développeur\\",\\"scoreCorrespondance\\":85,\\"anneesExperienceEstimees\\":3,\\"justificationScore\\":\\"Bon profil\\",\\"motsCles\\":[\\"Java\\",\\"Spring\\"]}"
                }
              ]
            }
          }
        ]
      }
      """;

  private GeminiCvAnalysisProvider provider(MockRestServiceServer[] serveurCapture) {
    RestClient.Builder builder = RestClient.builder();
    serveurCapture[0] = MockRestServiceServer.bindTo(builder).build();
    return new GeminiCvAnalysisProvider("fake-key", "gemini-2.0-flash", builder.build());
  }

  @Test
  void analyseUnCvEtParseLaReponseJson() throws Exception {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    GeminiCvAnalysisProvider provider = provider(serveur);
    serveur[0]
        .expect(requestTo(containsString("generateContent")))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(CORPS_REPONSE_VALIDE, MediaType.APPLICATION_JSON));

    AnalyseResultat resultat =
        provider.analyser(
            new ByteArrayResource("contenu cv".getBytes()),
            "application/pdf",
            new ContexteOffre("Développeur Java", "desc", List.of("Java", "Spring")));

    assertThat(resultat.prenom()).isEqualTo("Amine");
    assertThat(resultat.nom()).isEqualTo("Bennani");
    assertThat(resultat.telephone()).isNull();
    assertThat(resultat.motsCles()).containsExactly("Java", "Spring");
    assertThat(resultat.scoreCorrespondance()).isEqualByComparingTo("85");
  }

  @Test
  void leveUneExceptionSiAucunCandidatDansLaReponse() {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    GeminiCvAnalysisProvider provider = provider(serveur);
    serveur[0]
        .expect(requestTo(containsString("generateContent")))
        .andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                provider.analyser(
                    new ByteArrayResource("contenu cv".getBytes()), "application/pdf", null))
        .isInstanceOf(CvAnalysisException.class);
  }

  @Test
  void leveUneExceptionSiLeTexteCandidatNestPasDuJsonValide() {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    GeminiCvAnalysisProvider provider = provider(serveur);
    String reponseTexteInvalide =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"pas du json\"}]}}]}";
    serveur[0]
        .expect(requestTo(containsString("generateContent")))
        .andRespond(withSuccess(reponseTexteInvalide, MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                provider.analyser(
                    new ByteArrayResource("contenu cv".getBytes()), "application/pdf", null))
        .isInstanceOf(CvAnalysisException.class);
  }

  @Test
  void disponibleRenvoieVraiPourGemini() {
    GeminiCvAnalysisProvider provider =
        new GeminiCvAnalysisProvider("fake-key", "gemini-2.0-flash", RestClient.create());
    assertThat(provider.disponible()).isTrue();
  }

  @Test
  void noopProviderNestJamaisDisponibleEtLeveUneException() {
    NoopCvAnalysisProvider noop = new NoopCvAnalysisProvider();
    assertThat(noop.disponible()).isFalse();
    assertThatThrownBy(
            () -> noop.analyser(new ByteArrayResource("x".getBytes()), "application/pdf", null))
        .isInstanceOf(CvAnalysisException.class);
  }
}
