package ma.hbdev.rh.attendance;

/** Appareil kiosque sans activation valide (absente, révoquée, ou déléguant hors fenêtre). */
class AppareilNonActiveException extends RuntimeException {
  AppareilNonActiveException() {
    super("Cet appareil n'est pas activé pour le kiosque de pointage");
  }
}
