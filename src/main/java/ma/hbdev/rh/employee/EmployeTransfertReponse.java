package ma.hbdev.rh.employee;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeTransfertReponse(
    UUID id,
    UUID ancienDepartementId,
    UUID nouveauDepartementId,
    UUID ancienManagerId,
    UUID nouveauManagerId,
    LocalDate dateEffet,
    Instant creeLe) {

  static EmployeTransfertReponse depuis(EmployeTransfert transfert) {
    return new EmployeTransfertReponse(
        transfert.getId(),
        transfert.getAncienDepartementId(),
        transfert.getNouveauDepartementId(),
        transfert.getAncienManagerId(),
        transfert.getNouveauManagerId(),
        transfert.getDateEffet(),
        transfert.getCreeLe());
  }
}
