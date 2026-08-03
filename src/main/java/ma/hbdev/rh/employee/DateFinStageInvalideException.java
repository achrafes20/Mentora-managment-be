package ma.hbdev.rh.employee;

/**
 * EF-DOC-12 — date de fin de stage prévue réservée aux STAGIAIRE/STAGIAIRE_REMUNERE (CHECK aussi
 * posé en base).
 */
class DateFinStageInvalideException extends RuntimeException {

  DateFinStageInvalideException() {
    super("La date de fin de stage prévue n'est applicable qu'aux employés en stage");
  }
}
