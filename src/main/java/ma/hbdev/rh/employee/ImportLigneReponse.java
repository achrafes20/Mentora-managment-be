package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

public record ImportLigneReponse(
    int numeroLigne,
    String statut,
    String action,
    JsonNode donnees,
    List<String> erreurs,
    UUID entiteId) {

  static ImportLigneReponse depuis(ImportLigneResultat resultat, ObjectMapper objectMapper) {
    return new ImportLigneReponse(
        resultat.numeroLigne(),
        resultat.statut().name(),
        resultat.action().name(),
        objectMapper.valueToTree(resultat.donnees()),
        resultat.erreurs(),
        resultat.entiteId());
  }

  static ImportLigneReponse depuis(ImportLigne ligne) {
    return new ImportLigneReponse(
        ligne.getNumeroLigne(),
        ligne.getStatut().name(),
        ligne.getAction().name(),
        ligne.getDonneesBrutes(),
        ligne.getErreurs() == null ? null : List.of(ligne.getErreurs().split("; ")),
        ligne.getEntiteId());
  }
}
