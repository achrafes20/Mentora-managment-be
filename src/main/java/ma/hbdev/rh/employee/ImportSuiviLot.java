package ma.hbdev.rh.employee;

import java.util.HashSet;
import java.util.Set;

/**
 * Suivi des clés métier déjà rencontrées au sein du même lot en cours de traitement. Nécessaire
 * même en dry-run : sans ça, deux lignes portant le même nom de département / e-mail employé dans
 * le même fichier seraient toutes deux rapportées "CREATION" en simulation, alors que l'exécution
 * réelle (qui voit ses propres écritures au fil de l'eau via l'auto-flush Hibernate) ne créerait la
 * seconde qu'une fois — divergence entre le rapport de simulation et le résultat réel.
 */
class ImportSuiviLot {

  private final Set<String> clesVues = new HashSet<>();

  /**
   * @return {@code true} si c'est la première fois que cette clé est vue dans ce lot.
   */
  boolean premiereFois(String cle) {
    return clesVues.add(cle.toLowerCase());
  }
}
