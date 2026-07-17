package ma.hbdev.rh.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Implémentation Gemini de {@link CvAnalysisProvider} : appel REST direct à l'API Generative
 * Language ({@code generateContent}), sans SDK vendor — même principe que {@code MailService} qui
 * appelle le webhook n8n via {@code RestClient}. Le CV est envoyé tel quel en pièce jointe inline
 * (base64) : Gemini traite nativement le PDF/DOCX, pas besoin d'extraction de texte côté backend.
 *
 * <p>Package-privé : seule {@link CvAnalysisConfig} sait qu'il s'agit de Gemini (quarantine vendor,
 * ai-instructions.md règle 6).
 */
class GeminiCvAnalysisProvider implements CvAnalysisProvider {

  private static final String PROMPT_INSTRUCTIONS =
      "Tu analyses un CV de candidat pour un poste de recrutement. Réponds UNIQUEMENT avec un "
          + "objet JSON valide, sans texte ni markdown autour, avec exactement ces clés : "
          + "prenom, nom, email, telephone, intitulePoste (poste actuel ou recherché détecté sur "
          + "le CV), scoreCorrespondance (nombre 0-100, correspondance avec l'offre décrite "
          + "ci-dessous), anneesExperienceEstimees (nombre), justificationScore (texte court "
          + "expliquant le score), motsCles (tableau de chaînes : compétences et technologies "
          + "détectées sur le CV). Utilise null pour tout champ non détectable.\n\n";

  private final String apiKey;
  private final String model;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  GeminiCvAnalysisProvider(String apiKey, String model, RestClient restClient) {
    this.apiKey = apiKey;
    this.model = model;
    this.restClient = restClient;
  }

  @Override
  public boolean disponible() {
    return true;
  }

  @Override
  public AnalyseResultat analyser(Resource cv, String typeMime, ContexteOffre contexte)
      throws CvAnalysisException {
    String texteJson = appellerGemini(cv, typeMime, contexte);
    return parseResultat(texteJson);
  }

  private String appellerGemini(Resource cv, String typeMime, ContexteOffre contexte)
      throws CvAnalysisException {
    try {
      String base64Cv = Base64.getEncoder().encodeToString(cv.getInputStream().readAllBytes());
      Map<String, Object> corps =
          Map.of(
              "contents",
              List.of(
                  Map.of(
                      "parts",
                      List.of(
                          Map.of("text", construirePrompt(contexte)),
                          Map.of("inline_data", Map.of("mime_type", typeMime, "data", base64Cv))))),
              "generationConfig",
              Map.of("responseMimeType", "application/json"));

      String url =
          "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
              .formatted(model, apiKey);

      JsonNode reponse =
          restClient
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .body(corps)
              .retrieve()
              .body(JsonNode.class);

      if (reponse == null) {
        throw new CvAnalysisException("Réponse Gemini vide");
      }
      String texte =
          reponse
              .path("candidates")
              .path(0)
              .path("content")
              .path("parts")
              .path(0)
              .path("text")
              .asText(null);
      if (texte == null || texte.isBlank()) {
        throw new CvAnalysisException("Réponse Gemini inattendue (pas de texte candidat)");
      }
      return texte;
    } catch (CvAnalysisException e) {
      throw e;
    } catch (Exception e) {
      throw new CvAnalysisException("Échec de l'appel à l'API Gemini", e);
    }
  }

  private String construirePrompt(ContexteOffre contexte) {
    if (contexte == null) {
      return PROMPT_INSTRUCTIONS;
    }
    return PROMPT_INSTRUCTIONS
        + "Offre visée : "
        + nullVersVide(contexte.intitule())
        + "\nDescription : "
        + nullVersVide(contexte.description())
        + "\nMots-clés requis : "
        + (contexte.motsClesRequis() == null ? "" : String.join(", ", contexte.motsClesRequis()))
        + "\n";
  }

  private AnalyseResultat parseResultat(String texteJson) throws CvAnalysisException {
    try {
      JsonNode node = objectMapper.readTree(texteJson);
      List<String> motsCles = new ArrayList<>();
      if (node.has("motsCles") && node.get("motsCles").isArray()) {
        node.get("motsCles").forEach(n -> motsCles.add(n.asText()));
      }
      return new AnalyseResultat(
          texteOuNull(node, "prenom"),
          texteOuNull(node, "nom"),
          texteOuNull(node, "email"),
          texteOuNull(node, "telephone"),
          texteOuNull(node, "intitulePoste"),
          nombreOuNull(node, "scoreCorrespondance"),
          nombreOuNull(node, "anneesExperienceEstimees"),
          texteOuNull(node, "justificationScore"),
          motsCles);
    } catch (Exception e) {
      throw new CvAnalysisException("Réponse Gemini illisible (JSON invalide)", e);
    }
  }

  private static String nullVersVide(String valeur) {
    return valeur == null ? "" : valeur;
  }

  private static String texteOuNull(JsonNode node, String champ) {
    JsonNode valeur = node.get(champ);
    return (valeur == null || valeur.isNull()) ? null : valeur.asText();
  }

  private static BigDecimal nombreOuNull(JsonNode node, String champ) {
    JsonNode valeur = node.get(champ);
    return (valeur == null || valeur.isNull() || !valeur.isNumber())
        ? null
        : BigDecimal.valueOf(valeur.asDouble());
  }
}
