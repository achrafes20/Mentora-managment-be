package ma.hbdev.rh.employee;

/** Fichier d'import illisible, vide, ou dans un format non supporté (EF-EMP-07). */
class ImportFichierInvalideException extends RuntimeException {

  ImportFichierInvalideException(String message) {
    super(message);
  }

  ImportFichierInvalideException(String message, Throwable cause) {
    super(message, cause);
  }
}
