package ma.hbdev.rh.shared.ai;

import org.springframework.core.io.Resource;

/**
 * Utilisé quand aucune clé API Gemini n'est configurée — l'application démarre et le pipeline de
 * recrutement continue de fonctionner, les candidatures restent en "analyse en attente"
 * (EF-REC-05). Jamais instancié directement par une feature, seulement par {@link
 * CvAnalysisConfig}.
 */
class NoopCvAnalysisProvider implements CvAnalysisProvider {

  @Override
  public boolean disponible() {
    return false;
  }

  @Override
  public AnalyseResultat analyser(Resource cv, String typeMime, ContexteOffre contexte)
      throws CvAnalysisException {
    throw new CvAnalysisException("Analyse IA indisponible : aucune clé API Gemini configurée");
  }
}
