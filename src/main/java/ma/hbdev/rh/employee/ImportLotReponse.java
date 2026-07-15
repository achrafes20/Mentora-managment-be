package ma.hbdev.rh.employee;

import java.time.Instant;
import java.util.UUID;

public record ImportLotReponse(
    UUID id,
    String cible,
    String mode,
    String nomFichier,
    int nbLignesTotal,
    int nbLignesValides,
    int nbLignesErreur,
    UUID executePar,
    Instant creeLe) {

  static ImportLotReponse depuis(ImportLot lot) {
    return new ImportLotReponse(
        lot.getId(),
        lot.getCible().name(),
        lot.getMode().name(),
        lot.getNomFichier(),
        lot.getNbLignesTotal(),
        lot.getNbLignesValides(),
        lot.getNbLignesErreur(),
        lot.getExecutePar(),
        lot.getCreeLe());
  }
}
