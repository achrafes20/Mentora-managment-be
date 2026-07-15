package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** DTO de réponse pour un horaire de référence. */
public record HoraireReferenceReponse(
    UUID id,
    LocalTime heureDebutMatin,
    LocalTime heureFinMatin,
    LocalTime heureDebutApresMidi,
    LocalTime heureFinApresMidi,
    int toleranceMinutes,
    LocalDate dateEffet) {

  static HoraireReferenceReponse depuis(HoraireReference h) {
    return new HoraireReferenceReponse(
        h.getId(),
        h.getHeureDebutMatin(),
        h.getHeureFinMatin(),
        h.getHeureDebutApresMidi(),
        h.getHeureFinApresMidi(),
        h.getToleranceMinutes(),
        h.getDateEffet());
  }
}
