package ma.hbdev.rh.employee;

/** Mapping colonne → champ cible incomplet (un champ requis n'a pas de colonne source associée). */
class ImportMappingInvalideException extends RuntimeException {

  ImportMappingInvalideException(String message) {
    super(message);
  }
}
