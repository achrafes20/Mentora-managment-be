package ma.hbdev.rh.employee;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-suggestion du mapping colonne source → champ cible, par comparaison de libellés normalisés
 * (accents/casse/ponctuation ignorés). Purement indicatif : l'assistant frontend affiche cette
 * suggestion pré-remplie mais l'utilisateur RH peut toujours la corriger avant de lancer le
 * dry-run.
 */
final class ColonneMatcher {

  private ColonneMatcher() {}

  static Map<String, Integer> suggererMapping(List<String> entetes, List<ImportChampSpec> champs) {
    Map<String, Integer> mapping = new HashMap<>();
    Set<Integer> colonnesUtilisees = new HashSet<>();
    List<String> entetesNormalisees = entetes.stream().map(ColonneMatcher::normaliser).toList();

    for (ImportChampSpec champ : champs) {
      List<String> synonymesNormalises =
          champ.synonymes().stream().map(ColonneMatcher::normaliser).toList();

      Integer meilleureColonne = null;
      for (int i = 0; i < entetesNormalisees.size(); i++) {
        if (colonnesUtilisees.contains(i)) {
          continue;
        }
        String entete = entetesNormalisees.get(i);
        if (entete.isBlank()) {
          continue;
        }
        if (synonymesNormalises.contains(entete)) {
          meilleureColonne = i;
          break;
        }
        if (meilleureColonne == null && synonymesNormalises.stream().anyMatch(entete::contains)) {
          meilleureColonne = i;
        }
      }
      if (meilleureColonne != null) {
        mapping.put(champ.cle(), meilleureColonne);
        colonnesUtilisees.add(meilleureColonne);
      }
    }
    return mapping;
  }

  static String normaliser(String valeur) {
    if (valeur == null) {
      return "";
    }
    String sansAccents = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return sansAccents.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
  }
}
