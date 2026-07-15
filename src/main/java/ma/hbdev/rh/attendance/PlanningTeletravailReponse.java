package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO de réponse pour un planning de télétravail. */
public record PlanningTeletravailReponse(
    UUID id,
    UUID employeId,
    LocalDate dateDebut,
    LocalDate dateFin,
    List<TypeJourSemaine> jours,
    Instant creeLe) {

  static PlanningTeletravailReponse depuis(PlanningTeletravail p) {
    return new PlanningTeletravailReponse(
        p.getId(),
        p.getEmployeId(),
        p.getDateDebut(),
        p.getDateFin(),
        p.getJours().stream().map(PlanningTeletravailJour::getJourSemaine).toList(),
        p.getCreeLe());
  }
}
