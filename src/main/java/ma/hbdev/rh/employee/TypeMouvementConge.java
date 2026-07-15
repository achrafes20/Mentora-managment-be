package ma.hbdev.rh.employee;

/**
 * Miroir Java du type Postgres {@code type_mouvement_conge}. Seul {@code initialisation} est
 * utilisé par l'import (EF-EMP-07) ; les trois autres valeurs appartiennent au ledger de congés
 * (EF-ADM, T3.A2, pas encore construit) et sont déclarées ici uniquement pour rester fidèles à
 * l'énumération Postgres complète.
 */
enum TypeMouvementConge {
  initialisation,
  consommation,
  recredit,
  ajustement
}
