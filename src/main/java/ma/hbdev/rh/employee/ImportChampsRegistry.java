package ma.hbdev.rh.employee;

import java.util.List;
import java.util.Map;

/**
 * Registre statique des champs cibles par {@link ImportCible} — le cœur de ce qui rend l'import
 * agnostique du schéma Excel/CSV réel de la RH (cf. décision 2026-07-15) : on ne suppose jamais
 * l'ordre ou le nom exact des colonnes source, on propose un mapping (auto-suggéré par synonymes,
 * ajustable dans l'assistant frontend) vers ces champs fixes, connus de notre modèle de données.
 */
final class ImportChampsRegistry {

  private static final Map<ImportCible, List<ImportChampSpec>> CHAMPS =
      Map.of(
          ImportCible.DEPARTEMENTS,
          List.of(
              new ImportChampSpec(
                  "nom",
                  "Nom du département",
                  true,
                  TypeChampImport.TEXTE,
                  List.of("nom", "nom departement", "departement", "service", "nom du service"))),
          ImportCible.EMPLOYES,
          List.of(
              new ImportChampSpec(
                  "nom",
                  "Nom",
                  true,
                  TypeChampImport.TEXTE,
                  List.of("nom", "nom de famille", "last name", "lastname")),
              new ImportChampSpec(
                  "prenom",
                  "Prénom",
                  true,
                  TypeChampImport.TEXTE,
                  List.of("prenom", "prénom", "first name", "firstname")),
              new ImportChampSpec(
                  "email",
                  "E-mail",
                  false,
                  TypeChampImport.EMAIL,
                  List.of("email", "e-mail", "mail", "courriel")),
              new ImportChampSpec(
                  "telephone",
                  "Téléphone",
                  false,
                  TypeChampImport.TEXTE,
                  List.of("telephone", "téléphone", "tel", "phone", "gsm")),
              new ImportChampSpec(
                  "poste",
                  "Poste",
                  false,
                  TypeChampImport.TEXTE,
                  List.of("poste", "fonction", "job title", "position", "titre")),
              new ImportChampSpec(
                  "departementNom",
                  "Département",
                  true,
                  TypeChampImport.TEXTE,
                  List.of("departement", "département", "service", "direction")),
              new ImportChampSpec(
                  "dateEmbauche",
                  "Date d'embauche",
                  true,
                  TypeChampImport.DATE,
                  List.of(
                      "date embauche",
                      "date d'embauche",
                      "date d'entree",
                      "date entree",
                      "hire date")),
              new ImportChampSpec(
                  "typeContrat",
                  "Type de contrat",
                  true,
                  TypeChampImport.ENUM,
                  List.of("type contrat", "type de contrat", "contrat", "contract type")),
              new ImportChampSpec(
                  "dateFinContratPrevue",
                  "Date de fin de contrat prévue (CDD uniquement)",
                  false,
                  TypeChampImport.DATE,
                  List.of(
                      "date fin contrat",
                      "fin de contrat",
                      "date fin",
                      "contract end",
                      "fin contrat prevue"))),
          ImportCible.SOLDES_CONGES_INITIAUX,
          List.of(
              new ImportChampSpec(
                  "employeEmail",
                  "E-mail de l'employé",
                  true,
                  TypeChampImport.EMAIL,
                  List.of("email", "e-mail", "mail", "courriel", "email employe")),
              new ImportChampSpec(
                  "quantiteJours",
                  "Solde initial (jours)",
                  true,
                  TypeChampImport.NUMERIQUE,
                  List.of(
                      "solde",
                      "jours",
                      "solde initial",
                      "quantite jours",
                      "jours conges",
                      "solde conges")),
              new ImportChampSpec(
                  "commentaire",
                  "Commentaire",
                  false,
                  TypeChampImport.TEXTE,
                  List.of("commentaire", "note", "remarque", "observation"))));

  private ImportChampsRegistry() {}

  static List<ImportChampSpec> champsPour(ImportCible cible) {
    return CHAMPS.get(cible);
  }
}
