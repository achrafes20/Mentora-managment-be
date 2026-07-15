package ma.hbdev.rh.employee;

import java.util.List;

/** Contenu brut d'un fichier tableur parsé : en-têtes (première ligne) + lignes de données. */
record TableurBrut(List<String> entetes, List<List<String>> lignes) {}
