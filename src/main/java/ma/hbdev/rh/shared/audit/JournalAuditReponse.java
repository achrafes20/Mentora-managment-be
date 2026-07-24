package ma.hbdev.rh.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import ma.hbdev.rh.shared.event.ModuleAudit;

/** EF-CFG-04 : ligne d'audit exposée en lecture seule à l'écran de consultation. */
public record JournalAuditReponse(
    UUID id,
    UUID utilisateurId,
    String action,
    ModuleAudit module,
    String entiteType,
    UUID entiteId,
    JsonNode details,
    boolean enDelegation,
    UUID delegationId,
    Instant horodatage) {

  static JournalAuditReponse depuis(JournalAudit entree) {
    return new JournalAuditReponse(
        entree.getId(),
        entree.getUtilisateurId(),
        entree.getAction(),
        entree.getModule(),
        entree.getEntiteType(),
        entree.getEntiteId(),
        entree.getDetails(),
        entree.isEnDelegation(),
        entree.getDelegationId(),
        entree.getHorodatage());
  }
}
