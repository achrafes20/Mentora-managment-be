package ma.hbdev.rh.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Jamais de vraie clé OpenRouter en test/CI (DoD, ai-instructions.md) — {@code RestClient} est
 * intercepté par {@link MockRestServiceServer}, aucun appel réseau réel.
 */
class OpenRouterCvAnalysisProviderTest {

  private static final String CORPS_REPONSE_VALIDE =
      """
      {
        "choices": [
          {
            "message": {
              "content": "{\\"prenom\\":\\"Amine\\",\\"nom\\":\\"Bennani\\",\\"email\\":\\"amine@test.ma\\",\\"telephone\\":null,\\"intitulePoste\\":\\"Développeur\\",\\"scoreCorrespondance\\":85,\\"anneesExperienceEstimees\\":3,\\"justificationScore\\":\\"Bon profil\\",\\"motsCles\\":[\\"Java\\",\\"Spring\\"]}"
            }
          }
        ]
      }
      """;

  private OpenRouterCvAnalysisProvider provider(MockRestServiceServer[] serveurCapture) {
    RestClient.Builder builder = RestClient.builder();
    serveurCapture[0] = MockRestServiceServer.bindTo(builder).build();
    return new OpenRouterCvAnalysisProvider("fake-key", "openai/gpt-oss-20b:free", builder.build());
  }

  private static ByteArrayResource cvPdf(String texte) throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(50, 700);
        cs.showText(texte);
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return new ByteArrayResource(out.toByteArray());
    }
  }

  @Test
  void extraitLeTexteDuPdfEtAnalyseLaReponseJson() throws Exception {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);
    serveur[0]
        .expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer fake-key"))
        .andRespond(withSuccess(CORPS_REPONSE_VALIDE, MediaType.APPLICATION_JSON));

    AnalyseResultat resultat =
        provider.analyser(
            cvPdf("Amine Bennani - Developpeur Java"),
            "application/pdf",
            new ContexteOffre("Développeur Java", "desc", List.of("Java", "Spring")),
            null);

    assertThat(resultat.prenom()).isEqualTo("Amine");
    assertThat(resultat.nom()).isEqualTo("Bennani");
    assertThat(resultat.telephone()).isNull();
    assertThat(resultat.motsCles()).containsExactly("Java", "Spring");
    assertThat(resultat.scoreCorrespondance()).isEqualByComparingTo("85");
  }

  @Test
  void toleteUnJsonEntoureDeTexteOuMarkdown() throws Exception {
    // Contrairement à Gemini (JSON forcé), un modèle gratuit hétérogène peut ignorer la consigne
    // et entourer sa réponse de texte/```json``` — le parsing doit rester robuste à ça.
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);
    String corpsAvecMarkdown =
        """
        {"choices":[{"message":{"content":"Voici le résultat :\\n```json\\n{\\"prenom\\":\\"Amine\\",\\"nom\\":\\"Bennani\\",\\"email\\":null,\\"telephone\\":null,\\"intitulePoste\\":null,\\"scoreCorrespondance\\":70,\\"anneesExperienceEstimees\\":null,\\"justificationScore\\":null,\\"motsCles\\":[]}\\n```"}}]}
        """;
    serveur[0]
        .expect(requestTo(containsString("chat/completions")))
        .andRespond(withSuccess(corpsAvecMarkdown, MediaType.APPLICATION_JSON));

    AnalyseResultat resultat =
        provider.analyser(cvPdf("Amine Bennani"), "application/pdf", null, null);

    assertThat(resultat.prenom()).isEqualTo("Amine");
    assertThat(resultat.scoreCorrespondance()).isEqualByComparingTo("70");
  }

  @Test
  void inclutLeMessageDuCandidatDansLePromptEnvoye() throws Exception {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);
    serveur[0]
        .expect(requestTo(containsString("chat/completions")))
        .andExpect(
            content().string(containsString("Disponible immédiatement, motivé par le poste")))
        .andRespond(withSuccess(CORPS_REPONSE_VALIDE, MediaType.APPLICATION_JSON));

    provider.analyser(
        cvPdf("Amine Bennani"),
        "application/pdf",
        null,
        "Disponible immédiatement, motivé par le poste.");
  }

  @Test
  void leveUneExceptionPourUnFormatDeFichierNonSupporte() {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);

    assertThatThrownBy(
            () ->
                provider.analyser(
                    new ByteArrayResource("une image".getBytes()), "image/png", null, null))
        .isInstanceOf(CvAnalysisException.class)
        .hasMessageContaining("non supporté");
  }

  @Test
  void leveUneExceptionSiAucunChoixDansLaReponse() throws Exception {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);
    serveur[0]
        .expect(requestTo(containsString("chat/completions")))
        .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.analyser(cvPdf("cv vide"), "application/pdf", null, null))
        .isInstanceOf(CvAnalysisException.class);
  }

  @Test
  void leveUneExceptionSiAucunObjetJsonDansLaReponse() throws Exception {
    MockRestServiceServer[] serveur = new MockRestServiceServer[1];
    OpenRouterCvAnalysisProvider provider = provider(serveur);
    String corps = "{\"choices\":[{\"message\":{\"content\":\"pas du json du tout\"}}]}";
    serveur[0]
        .expect(requestTo(containsString("chat/completions")))
        .andRespond(withSuccess(corps, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.analyser(cvPdf("cv"), "application/pdf", null, null))
        .isInstanceOf(CvAnalysisException.class);
  }

  @Test
  void disponibleRenvoieVraiPourOpenRouter() {
    OpenRouterCvAnalysisProvider provider =
        new OpenRouterCvAnalysisProvider(
            "fake-key", "openai/gpt-oss-20b:free", RestClient.create());
    assertThat(provider.disponible()).isTrue();
  }
}
