package ma.hbdev.rh.shared.ai;

import org.springframework.core.io.Resource;

/**
 * Point d'entrée unique pour toute feature qui a besoin d'analyser un CV. Quarantine le vendor IA
 * derrière cette interface (ai-instructions.md règle 6) — les features consommatrices ne
 * connaissent jamais Gemini, même principe que {@code FileStorageService} pour le stockage.
 *
 * <p>{@link #disponible()} doit être vérifié avant tout appel à {@link #analyser} : l'application
 * doit démarrer et fonctionner sans clé API configurée (EF-REC-05).
 */
public interface CvAnalysisProvider {

  boolean disponible();

  /**
   * @throws CvAnalysisException si l'analyse échoue (réponse invalide, erreur réseau...) — ne doit
   *     jamais bloquer le pipeline de recrutement côté appelant (EF-REC-05).
   */
  AnalyseResultat analyser(Resource cv, String typeMime, ContexteOffre contexte)
      throws CvAnalysisException;
}
