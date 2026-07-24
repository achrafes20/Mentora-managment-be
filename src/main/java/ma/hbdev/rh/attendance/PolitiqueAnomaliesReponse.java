package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

record PolitiqueAnomaliesReponse(
    UUID id, int seuilAnomalies, int periodeJours, UUID modifiePar, Instant modifieLe) {
  static PolitiqueAnomaliesReponse depuis(PolitiqueAnomalies politique) {
    return new PolitiqueAnomaliesReponse(
        politique.getId(),
        politique.getSeuilAnomalies(),
        politique.getPeriodeJours(),
        politique.getModifiePar(),
        politique.getModifieLe());
  }
}
