package ma.hbdev.rh.shared.ai;

/**
 * Échec d'analyse IA (réponse vendor invalide, erreur réseau...) — jamais une RuntimeException :
 * force l'appelant à gérer explicitement la dégradation gracieuse (EF-REC-05).
 */
public class CvAnalysisException extends Exception {

  public CvAnalysisException(String message) {
    super(message);
  }

  public CvAnalysisException(String message, Throwable cause) {
    super(message, cause);
  }
}
