package ma.hbdev.rh.attendance;

/**
 * Code d'activation jamais saisi dans le délai imparti ({@code
 * app.security.kiosque.expiration-code-heures}) — NFR-UX-02. Distinct de {@link
 * CodeActivationDejaUtiliseException} : celui-ci n'a jamais été consommé, il a simplement expiré ;
 * distinct aussi d'un code invalide, pour ne pas laisser croire à une faute de frappe.
 */
class CodeActivationExpireException extends RuntimeException {
  CodeActivationExpireException() {
    super("Ce code a expiré, veuillez en générer un nouveau.");
  }
}
