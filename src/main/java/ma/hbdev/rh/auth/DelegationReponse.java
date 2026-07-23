package ma.hbdev.rh.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DelegationReponse(
    UUID id,
    UUID adminDelegantId,
    UUID delegueId,
    LocalDate dateDebut,
    LocalDate dateFin,
    StatutDelegation statut,
    UUID revoqueParId,
    Instant revoqueLe,
    Instant creeLe) {
  public static DelegationReponse depuis(DelegationApprobation delegation) {
    return new DelegationReponse(
        delegation.getId(),
        delegation.getAdminDelegantId(),
        delegation.getDelegueId(),
        delegation.getDateDebut(),
        delegation.getDateFin(),
        delegation.statutEffectif(),
        delegation.getRevoqueParId(),
        delegation.getRevoqueLe(),
        delegation.getCreeLe());
  }
}
