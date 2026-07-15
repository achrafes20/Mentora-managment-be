package ma.hbdev.rh.employee;

import java.util.List;
import java.util.UUID;

/** Résultat d'un lot d'import (dry-run ou réel), avant conversion en DTO de réponse HTTP. */
record ImportExecutionResultat(
    UUID lotId,
    ImportCible cible,
    ModeImportLot mode,
    int totalLignes,
    int lignesValides,
    int lignesErreur,
    List<ImportLigneResultat> lignes) {}
