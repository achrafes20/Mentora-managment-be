package ma.hbdev.rh.attendance;

/** Code d'activation valide historiquement mais déjà consommé (activé ou révoqué) — NFR-UX-02. */
class CodeActivationDejaUtiliseException extends RuntimeException {
  CodeActivationDejaUtiliseException() {
    super("Ce code a déjà été utilisé.");
  }
}
