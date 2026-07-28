package ma.hbdev.rh.shared.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Implémentation OpenRouter de {@link CvAnalysisProvider} : le CV est d'abord converti en texte
 * brut par {@link CvTextExtractor}, puis envoyé à un modèle gratuit via l'API compatible OpenAI
 * d'OpenRouter (chat/completions) — appel REST direct, pas de SDK, même principe que {@code
 * MailService}/l'ancien {@code GeminiCvAnalysisProvider}.
 *
 * <p>Décision du 2026-07-28 (remplace Gemini) : le palier gratuit Gemini reste bloqué à un quota de
 * 0 sur tous les comptes/projets essayés, et l'activation de la facturation (seule remédiation
 * documentée par Google) n'est pas possible côté HB Développement (aucun moyen de paiement
 * disponible) — cf. avancement-projet.md pour le détail de l'investigation.
 *
 * <p>Package-privé : seule {@link CvAnalysisConfig} sait qu'il s'agit d'OpenRouter (quarantine
 * vendor, ai-instructions.md règle 6).
 */
class OpenRouterCvAnalysisProvider implements CvAnalysisProvider {

  private static final String PROMPT_INSTRUCTIONS =
      "Tu analyses le texte extrait d'un CV de candidat pour un poste de recrutement. Réponds "
          + "UNIQUEMENT avec un objet JSON valide, sans texte ni markdown autour, avec exactement "
          + "ces clés : prenom, nom, email, telephone, intitulePoste (poste actuel ou recherché "
          + "détecté sur le CV), scoreCorrespondance (nombre 0-100, correspondance avec l'offre "
          + "décrite ci-dessous), anneesExperienceEstimees (nombre), justificationScore (texte "
          + "court expliquant le score), motsCles (tableau de chaînes : compétences et "
          + "technologies détectées sur le CV). Utilise null pour tout champ non détectable.\n\n";

  private static final String URL_CHAT_COMPLETIONS =
      "https://openrouter.ai/api/v1/chat/completions";

  private final String apiKey;
  private final String model;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  OpenRouterCvAnalysisProvider(String apiKey, String model, RestClient restClient) {
    this.apiKey = apiKey;
    this.model = model;
    this.restClient = restClient;
  }

  @Override
  public boolean disponible() {
    return true;
  }

  @Override
  public AnalyseResultat analyser(
      Resource cv, String typeMime, ContexteOffre contexte, String messageCandidat)
      throws CvAnalysisException {
    String texteCv = CvTextExtractor.extraire(cv, typeMime);
    String texteJson = appellerOpenRouter(texteCv, contexte, messageCandidat);
    return parseResultat(texteJson);
  }

  private String appellerOpenRouter(String texteCv, ContexteOffre contexte, String messageCandidat)
      throws CvAnalysisException {
    try {
      String contenu = construirePrompt(contexte) + "CV (texte extrait) :\n" + texteCv;
      if (messageCandidat != null && !messageCandidat.isBlank()) {
        // Contexte complémentaire écrit par le candidat lui-même (disponibilité, motivation...) —
        // jamais dans le CV, peut influencer le score/la justification autant que le CV lui-même.
        contenu +=
            "\n\nMessage du candidat (corps de l'e-mail de candidature) :\n" + messageCandidat;
      }
      Map<String, Object> corps =
          Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", contenu)));

      JsonNode reponse =
          restClient
              .post()
              .uri(URL_CHAT_COMPLETIONS)
              .header("Authorization", "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(corps)
              .retrieve()
              .body(JsonNode.class);

      if (reponse == null) {
        throw new CvAnalysisException("Réponse OpenRouter vide");
      }
      String texte = reponse.path("choices").path(0).path("message").path("content").asText(null);
      if (texte == null || texte.isBlank()) {
        throw new CvAnalysisException("Réponse OpenRouter inattendue (pas de contenu candidat)");
      }
      return texte;
    } catch (CvAnalysisException e) {
      throw e;
    } catch (Exception e) {
      throw new CvAnalysisException("Échec de l'appel à l'API OpenRouter", e);
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
        + "\n\n";
  }

  private AnalyseResultat parseResultat(String texteJson) throws CvAnalysisException {
    try {
      JsonNode node = objectMapper.readTree(extraireJson(texteJson));
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
      throw new CvAnalysisException("Réponse OpenRouter illisible (JSON invalide)", e);
    }
  }

  // Contrairement à Gemini (responseMimeType forcé), un modèle gratuit hétérogène peut entourer
  // le JSON de texte/markdown malgré la consigne — on isole le premier objet { ... } plutôt que de
  // parser tel quel.
  private static String extraireJson(String texte) throws CvAnalysisException {
    int debut = texte.indexOf('{');
    int fin = texte.lastIndexOf('}');
    if (debut < 0 || fin < debut) {
      throw new CvAnalysisException("Aucun objet JSON trouvé dans la réponse OpenRouter");
    }
    return texte.substring(debut, fin + 1);
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
