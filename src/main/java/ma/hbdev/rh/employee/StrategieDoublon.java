package ma.hbdev.rh.employee;

/**
 * Comportement souhaité par l'Admin quand une ligne d'import correspond à une entité déjà existante
 * en base (dédoublonnage par e-mail pour EMPLOYES/SOLDES_CONGES_INITIAUX) — EF-EMP-07. Sans rapport
 * avec DEPARTEMENTS, qui ne réécrit jamais un département existant (juste {@code
 * AUCUN_CHANGEMENT}).
 */
enum StrategieDoublon {
  /** Comportement historique : la fiche existante est mise à jour avec les valeurs du fichier. */
  ECRASER,
  /** La ligne est ignorée (avertissement), la fiche existante reste inchangée. */
  IGNORER
}
