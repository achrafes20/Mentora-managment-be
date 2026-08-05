package ma.hbdev.rh.auth;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * NFR-SEC-03 : traçabilité des actions de sécurité sur un compte — création/modification/
 * (dés)activation par un Admin (EF-AUTH-16/17), et gestion de ses propres identifiants (mot de
 * passe, e-mail) ou verrouillage après tentatives échouées (EF-AUTH-02/03/04). Auparavant, ni
 * {@code AuthService} ni {@code UserService} ne publiaient d'événement métier — ces actions
 * n'apparaissaient nulle part dans {@code journal_audit}, malgré la règle 5 d'ai-instructions.md.
 *
 * <p>Volontairement absent : connexion réussie (déjà tracée par {@code sessions_utilisateur}, une
 * ligne par login noierait le journal) et déconnexion (aucune valeur de traçabilité).
 */
record CompteEvent(UUID compteId, String action, String email) implements EvenementMetier {

  static CompteEvent creation(UUID compteId, String email) {
    return new CompteEvent(compteId, "creation", email);
  }

  static CompteEvent modification(UUID compteId, String email) {
    return new CompteEvent(compteId, "modification", email);
  }

  static CompteEvent desactivation(UUID compteId, String email) {
    return new CompteEvent(compteId, "desactivation", email);
  }

  static CompteEvent activation(UUID compteId, String email) {
    return new CompteEvent(compteId, "activation", email);
  }

  static CompteEvent motDePasseReinitialise(UUID compteId, String email) {
    return new CompteEvent(compteId, "mot_de_passe_reinitialise", email);
  }

  static CompteEvent motDePasseModifie(UUID compteId, String email) {
    return new CompteEvent(compteId, "mot_de_passe_modifie", email);
  }

  static CompteEvent emailModifie(UUID compteId, String email) {
    return new CompteEvent(compteId, "email_modifie", email);
  }

  static CompteEvent verrouille(UUID compteId, String email) {
    return new CompteEvent(compteId, "verrouille", email);
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.authentification;
  }

  @Override
  public String entiteType() {
    return "utilisateur";
  }

  @Override
  public UUID entiteId() {
    return compteId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("email", email);
  }
}
