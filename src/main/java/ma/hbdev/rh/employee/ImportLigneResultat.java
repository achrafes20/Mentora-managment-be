package ma.hbdev.rh.employee;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Résultat de traitement d'une ligne d'import (dry-run ou réel) — EF-EMP-07. */
record ImportLigneResultat(
    int numeroLigne,
    StatutLigneImport statut,
    ActionLigneImport action,
    Map<String, String> donnees,
    List<String> erreurs,
    UUID entiteId) {

  static ImportLigneResultat erreur(
      int numeroLigne, Map<String, String> donnees, List<String> erreurs) {
    return new ImportLigneResultat(
        numeroLigne, StatutLigneImport.ERREUR, ActionLigneImport.IGNOREE, donnees, erreurs, null);
  }
}
