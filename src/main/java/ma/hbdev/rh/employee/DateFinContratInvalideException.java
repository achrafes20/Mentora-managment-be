package ma.hbdev.rh.employee;

/**
 * EF-EMP-15 — date de fin de contrat prévue réservée aux CDD (CHECK aussi posé en base), et
 * obligatoire pour un CDD (sans quoi la surveillance J-15/J-3, T4.A1, ne se déclenche jamais pour
 * cet employé sans qu'aucune erreur ne le signale à la création).
 */
class DateFinContratInvalideException extends RuntimeException {

  private DateFinContratInvalideException(String message) {
    super(message);
  }

  static DateFinContratInvalideException nonApplicable() {
    return new DateFinContratInvalideException(
        "La date de fin de contrat prévue n'est applicable qu'aux employés en CDD");
  }

  static DateFinContratInvalideException requise() {
    return new DateFinContratInvalideException(
        "La date de fin de contrat prévue est obligatoire pour un contrat CDD");
  }
}
