package ma.hbdev.rh.employee;

/** EF-EMP-15 — date de fin de contrat prévue réservée aux CDD (CHECK aussi posé en base). */
class DateFinContratInvalideException extends RuntimeException {

  DateFinContratInvalideException() {
    super("La date de fin de contrat prévue n'est applicable qu'aux employés en CDD");
  }
}
