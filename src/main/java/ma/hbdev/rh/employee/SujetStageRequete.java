package ma.hbdev.rh.employee;

/**
 * EF-EMP-01 — modification ciblée du sujet de stage, seul champ qu'un Manager (dans son propre
 * département, cf. EmployeService#verifierPerimetreManager) est autorisé à modifier sur la fiche
 * d'un stagiaire, contrairement au reste de la fiche réservé à l'Admin.
 */
public record SujetStageRequete(String sujetStage) {}
