package ma.hbdev.rh.auth;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * NFR-SEC-03 : traçabilité de la délégation elle-même (qui peut approuver quoi, pour quelle
 * période) — distinct de {@link DelegationNotificationManagerEvent}, qui ne sert qu'à notifier les
 * Managers (EF-AUTH-15, un événement par destinataire) et est volontairement exclu de journal_audit
 * (cf. son propre commentaire) pour ne pas dupliquer cette même ligne autant de fois qu'il y a de
 * Managers actifs. Sans celui-ci, seules les décisions *prises pendant* une délégation étaient
 * tracées (journal_audit.en_delegation/delegation_id sur la décision elle-même, EF-AUTH-14) — la
 * délégation qui accordait ce droit n'apparaissait, elle, jamais.
 */
record DelegationModifieeEvent(
    UUID delegationId, String action, String adminDelegantNom, String delegueNom)
    implements EvenementMetier {

  static DelegationModifieeEvent creation(
      UUID delegationId, String adminDelegantNom, String delegueNom) {
    return new DelegationModifieeEvent(delegationId, "creation", adminDelegantNom, delegueNom);
  }

  static DelegationModifieeEvent revocation(
      UUID delegationId, String adminDelegantNom, String delegueNom) {
    return new DelegationModifieeEvent(delegationId, "revocation", adminDelegantNom, delegueNom);
  }

  static DelegationModifieeEvent expirationAutomatique(
      UUID delegationId, String adminDelegantNom, String delegueNom) {
    return new DelegationModifieeEvent(
        delegationId, "expiration_automatique", adminDelegantNom, delegueNom);
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.delegation;
  }

  @Override
  public String entiteType() {
    return "delegation_approbation";
  }

  @Override
  public UUID entiteId() {
    return delegationId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("adminDelegant", adminDelegantNom, "delegue", delegueNom);
  }
}
