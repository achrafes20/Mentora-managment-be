package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

/** Ligne de l'écran de gestion des activations kiosque — jamais le code ni le jeton d'appareil. */
public record KiosqueActivationReponse(
    UUID id,
    UUID emisPar,
    UUID delegationId,
    UUID employeId,
    Instant emisLe,
    StatutActivationKiosque statut,
    Instant activeeLe,
    Instant revoqueeLe,
    UUID revoqueePar) {

  static KiosqueActivationReponse depuis(KiosqueActivation a) {
    return new KiosqueActivationReponse(
        a.getId(),
        a.getEmisPar(),
        a.getDelegationId(),
        a.getEmployeId(),
        a.getEmisLe(),
        a.getStatut(),
        a.getActiveeLe(),
        a.getRevoqueeLe(),
        a.getRevoqueePar());
  }
}
