package ma.hbdev.rh.recruitment;

/** EF-REC-09 : seul le Manager assigné à l'entretien peut en saisir le résultat. */
class EntretienManagerNonAssigneException extends RuntimeException {

  EntretienManagerNonAssigneException() {
    super("Cet entretien n'est pas assigné au Manager connecté");
  }
}
