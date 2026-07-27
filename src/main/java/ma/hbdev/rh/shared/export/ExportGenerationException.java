package ma.hbdev.rh.shared.export;

/**
 * Enveloppe non vérifiée d'une erreur d'E/S survenue pendant la génération d'un export en mémoire.
 */
public class ExportGenerationException extends RuntimeException {

  public ExportGenerationException(Throwable cause) {
    super("Erreur lors de la génération du fichier d'export", cause);
  }
}
