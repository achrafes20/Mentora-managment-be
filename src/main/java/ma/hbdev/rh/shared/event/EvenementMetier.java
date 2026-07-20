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
}
