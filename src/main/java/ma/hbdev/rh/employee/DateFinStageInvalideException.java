package ma.hbdev.rh.employee;

/**
 * EF-DOC-12 — date de fin de stage prévue réservée aux STAGIAIRE/STAGIAIRE_REMUNERE (CHECK aussi
 * posé en base), et obligatoire pour un stagiaire (sans quoi la surveillance J-3, T4.A1, ne se
 * déclenche jamais pour cet employé sans qu'aucune erreur ne le signale à la création).
 */
class DateFinStageInvalideException extends RuntimeException {

  private DateFinStageInvalideException(String message) {
    super(message);
  }

  static DateFinStageInvalideException nonApplicable() {
    return new DateFinStageInvalideException(
        "La date de fin de stage prévue n'est applicable qu'aux employés en stage");
  }

  static DateFinStageInvalideException requise() {
    return new DateFinStageInvalideException(
        "La date de fin de stage prévue est obligatoire pour un employé en stage");
  }
}
