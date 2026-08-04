package ma.hbdev.rh.attendance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * NFR-UX-02 : génération d'un code d'activation kiosque, ou activation/révocation d'un appareil. Ne
 * transporte jamais le code en clair (ni dans {@link #action}, ni dans {@link #details()}) — seul
 * {@code emisPar} (qui est derrière l'action) est audité, jamais le secret lui-même.
 */
record KiosqueActivationEvent(UUID activationId, String action, UUID emisPar, UUID delegationId)
    implements EvenementMetier {

  static KiosqueActivationEvent generation(UUID activationId, UUID emisPar, UUID delegationId) {
    return new KiosqueActivationEvent(activationId, "generation", emisPar, delegationId);
  }

  static KiosqueActivationEvent activation(UUID activationId, UUID emisPar, UUID delegationId) {
    return new KiosqueActivationEvent(activationId, "activation", emisPar, delegationId);
  }

  static KiosqueActivationEvent revocation(UUID activationId, UUID revoqueePar) {
    return new KiosqueActivationEvent(activationId, "revocation", revoqueePar, null);
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.presence;
  }

  @Override
  public String entiteType() {
    return "kiosque_activation";
  }

  @Override
  public UUID entiteId() {
    return activationId;
  }

  @Override
  public Map<String, Object> details() {
    Map<String, Object> details = new HashMap<>();
    details.put("emisPar", emisPar);
    if (delegationId != null) {
      details.put("delegationId", delegationId);
    }
    return details;
  }
}
