package ma.hbdev.rh.shared.event;

import java.util.Map;
import java.util.UUID;

/** Contrat commun consomme par les ecouteurs transverses notification et audit. */
public interface EvenementMetier {

  String action();

  ModuleAudit module();

  String entiteType();

  UUID entiteId();

  default Map<String, Object> details() {
    return Map.of();
  }

  /** Retourne {@code null} pour un evenement uniquement audite, sans destinataire fonctionnel. */
  default NotificationMetier notification() {
    return null;
  }

  /**
   * Faux pour un evenement systeme sans decision humaine derriere (ex. franchissement d'un seuil
   * calcule, transition automatique de pipeline) — reste notifiable si {@link #notification()} le
   * prevoit, mais n'encombre pas journal_audit d'une ligne sans valeur de tracabilite (qui a fait
   * quoi). Vrai par defaut : c'est l'exception qui doit se signaler, pas l'inverse.
   */
  default boolean audite() {
    return true;
  }

  /**
   * EF-AUTH-14 : vrai pour une action d'approbation, de rejet ou de décision de recrutement.
   * L'écouteur d'audit s'en sert pour marquer l'entrée comme réalisée en délégation lorsque
   * l'auteur courant est un délégué actif — jamais pour les actions de gestion de la délégation
   * elle-même (EF-AUTH-12).
   */
  default boolean decisionDelegable() {
    return false;
  }
}
