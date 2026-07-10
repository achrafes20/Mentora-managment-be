package ma.hbdev.rh.employee;

import java.time.Instant;
import java.util.UUID;

public record DepartementReponse(
    UUID id, String nom, UUID managerId, String statut, Instant creeLe, Instant modifieLe) {

  static DepartementReponse depuis(Departement departement) {
    return new DepartementReponse(
        departement.getId(),
        departement.getNom(),
        departement.getManagerId(),
        departement.getStatut().name(),
        departement.getCreeLe(),
        departement.getModifieLe());
  }
}
