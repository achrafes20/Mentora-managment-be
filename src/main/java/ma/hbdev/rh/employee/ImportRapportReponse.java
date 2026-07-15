package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

public record ImportRapportReponse(
    UUID lotId,
    String cible,
    String mode,
    int totalLignes,
    int lignesValides,
    int lignesErreur,
    List<ImportLigneReponse> lignes) {

  static ImportRapportReponse depuis(ImportExecutionResultat resultat, ObjectMapper objectMapper) {
    return new ImportRapportReponse(
        resultat.lotId(),
        resultat.cible().name(),
        resultat.mode().name(),
        resultat.totalLignes(),
        resultat.lignesValides(),
        resultat.lignesErreur(),
        resultat.lignes().stream().map(l -> ImportLigneReponse.depuis(l, objectMapper)).toList());
  }

  static ImportRapportReponse depuis(ImportLot lot, List<ImportLigne> lignes) {
    return new ImportRapportReponse(
        lot.getId(),
        lot.getCible().name(),
        lot.getMode().name(),
        lot.getNbLignesTotal(),
        lot.getNbLignesValides(),
        lot.getNbLignesErreur(),
        lignes.stream().map(ImportLigneReponse::depuis).toList());
  }
}
