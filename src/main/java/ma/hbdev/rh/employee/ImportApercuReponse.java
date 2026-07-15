package ma.hbdev.rh.employee;

import java.util.List;
import java.util.Map;

/** Aperçu d'un fichier avant mapping — en-têtes détectées, quelques lignes, mapping suggéré. */
public record ImportApercuReponse(
    List<String> entetes,
    List<List<String>> apercuLignes,
    int totalLignes,
    List<ImportChampSpecReponse> champsCible,
    Map<String, Integer> mappingSuggere) {}
