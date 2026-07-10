package ma.hbdev.rh.shared.file;

/**
 * Fichier rejeté par la validation MIME/taille (NFR-SEC-07). Le message porte le motif du rejet.
 */
public class FichierInvalideException extends RuntimeException {

  public FichierInvalideException(String motif) {
    super(motif);
  }
}
