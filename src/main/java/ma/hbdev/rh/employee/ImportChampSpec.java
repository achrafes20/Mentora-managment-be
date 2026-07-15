package ma.hbdev.rh.employee;

import java.util.List;

/**
 * Description d'un champ cible pour une {@link ImportCible} donnée : ce que l'assistant d'import
 * propose de renseigner, et les en-têtes de colonnes plausibles pour l'auto-suggestion de mapping
 * (cf. {@link ImportChampsRegistry}).
 */
record ImportChampSpec(
    String cle, String libelle, boolean requis, TypeChampImport type, List<String> synonymes) {}
