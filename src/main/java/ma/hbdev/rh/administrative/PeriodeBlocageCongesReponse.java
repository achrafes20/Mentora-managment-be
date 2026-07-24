package ma.hbdev.rh.administrative;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record PeriodeBlocageCongesReponse(
    UUID id, LocalDate dateDebut, LocalDate dateFin, String libelle, UUID creePar, Instant creeLe) {
  static PeriodeBlocageCongesReponse depuis(PeriodeBlocageConges periode) {
    return new PeriodeBlocageCongesReponse(
        periode.getId(),
        periode.getDateDebut(),
        periode.getDateFin(),
        periode.getLibelle(),
        periode.getCreePar(),
        periode.getCreeLe());
  }
}
