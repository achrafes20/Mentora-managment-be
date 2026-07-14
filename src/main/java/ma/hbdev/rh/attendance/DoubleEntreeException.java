package ma.hbdev.rh.attendance;

/** Tentative de double scan d'entrée sans sortie intermédiaire (EF-ATT-02). */
class DoubleEntreeException extends RuntimeException {
  DoubleEntreeException() {
    super("Un scan d'entrée est déjà enregistré sans sortie correspondante");
  }
}
