package ma.hbdev.rh.employee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Traite une ligne de la cible {@link ImportCible#EMPLOYES} (EF-EMP-07). Clé de dédoublonnage :
 * e-mail (décision 2026-07-15, pas de matricule dans le modèle actuel). Le rattachement manager
 * n'est volontairement pas importé en v1 (champ optionnel partout ailleurs dans l'app) : à assigner
 * manuellement après import via l'écran de transfert existant.
 */
@Component
class EmployeImportProcessor {

  private final EmployeRepository employeRepository;
  private final EmployeService employeService;
  private final DepartementRepository departementRepository;

  EmployeImportProcessor(
      EmployeRepository employeRepository,
      EmployeService employeService,
      DepartementRepository departementRepository) {
    this.employeRepository = employeRepository;
    this.employeService = employeService;
    this.departementRepository = departementRepository;
  }

  ImportLigneResultat traiter(
      int numeroLigne, Map<String, String> donnees, boolean dryRun, ImportSuiviLot suivi) {
    List<String> erreurs = new ArrayList<>();

    String nom = valeur(donnees, "nom");
    String prenom = valeur(donnees, "prenom");
    String email = valeur(donnees, "email");
    String telephone = valeur(donnees, "telephone");
    String poste = valeur(donnees, "poste");
    String departementNom = valeur(donnees, "departementNom");

    if (nom == null || nom.isBlank()) {
      erreurs.add("Nom requis");
    }
    if (prenom == null || prenom.isBlank()) {
      erreurs.add("Prénom requis");
    }

    Departement departement = null;
    if (departementNom == null || departementNom.isBlank()) {
      erreurs.add("Département requis");
    } else {
      departement = departementRepository.findByNomIgnoreCase(departementNom).orElse(null);
      if (departement == null) {
        erreurs.add(
            "Département introuvable : \""
                + departementNom
                + "\" (importer les départements d'abord)");
      }
    }

    LocalDate dateEmbauche = ImportParseUtils.parserDate(valeur(donnees, "dateEmbauche"));
    if (dateEmbauche == null) {
      erreurs.add("Date d'embauche requise ou format non reconnu (attendu JJ/MM/AAAA)");
    }

    TypeContratEmploye typeContrat =
        ImportParseUtils.parserTypeContrat(valeur(donnees, "typeContrat"));
    if (typeContrat == null) {
      erreurs.add(
          "Type de contrat requis ou non reconnu (CDI, CDD, Stagiaire, Stagiaire rémunéré)");
    }

    LocalDate dateFinContratPrevue =
        ImportParseUtils.parserDate(valeur(donnees, "dateFinContratPrevue"));
    if (typeContrat != null) {
      if (typeContrat == TypeContratEmploye.CDD
          && valeur(donnees, "dateFinContratPrevue") != null
          && !valeur(donnees, "dateFinContratPrevue").isBlank()
          && dateFinContratPrevue == null) {
        erreurs.add("Date de fin de contrat prévue : format non reconnu (attendu JJ/MM/AAAA)");
      }
      if (typeContrat != TypeContratEmploye.CDD && dateFinContratPrevue != null) {
        erreurs.add("Date de fin de contrat prévue non applicable hors CDD");
        dateFinContratPrevue = null;
      }
    }

    if (!erreurs.isEmpty()) {
      return ImportLigneResultat.erreur(numeroLigne, donnees, erreurs);
    }

    boolean sansEmail = email == null || email.isBlank();
    if (!sansEmail && !suivi.premiereFois("employe:" + email)) {
      return new ImportLigneResultat(
          numeroLigne,
          StatutLigneImport.AVERTISSEMENT,
          ActionLigneImport.IGNOREE,
          donnees,
          List.of("E-mail en double dans le fichier — ligne déjà traitée, ignorée"),
          null);
    }

    try {
      var existant = sansEmail ? null : employeRepository.findByEmailIgnoreCase(email).orElse(null);
      if (existant == null) {
        UUID entiteId = null;
        if (!dryRun) {
          entiteId =
              employeService
                  .creer(
                      new EmployeRequete(
                          nom,
                          prenom,
                          email,
                          telephone,
                          poste,
                          departement.getId(),
                          null,
                          dateEmbauche,
                          typeContrat,
                          dateFinContratPrevue,
                          // EF-DOC-12 : date de fin de stage pas encore un champ mappé par
                          // l'assistant d'import (T2.B1) — hors périmètre de ce correctif.
                          null,
                          null,
                          null))
                  .getId();
        }
        StatutLigneImport statut =
            sansEmail ? StatutLigneImport.AVERTISSEMENT : StatutLigneImport.VALIDE;
        List<String> avertissements =
            sansEmail
                ? List.of(
                    "Pas d'e-mail : dédoublonnage impossible, une fiche sera recréée à chaque import")
                : null;
        return new ImportLigneResultat(
            numeroLigne, statut, ActionLigneImport.CREATION, donnees, avertissements, entiteId);
      }

      UUID entiteId = existant.getId();
      if (!dryRun) {
        employeService.modifier(
            entiteId,
            new EmployeModificationRequete(
                nom,
                prenom,
                email,
                telephone,
                poste,
                dateEmbauche,
                typeContrat,
                dateFinContratPrevue,
                null));
        if (!existant.getDepartement().getId().equals(departement.getId())) {
          employeService.transferer(
              entiteId,
              new TransfertRequete(departement.getId(), existant.getManagerId(), LocalDate.now()),
              CurrentUser.id().orElse(null));
        }
      }
      return new ImportLigneResultat(
          numeroLigne,
          StatutLigneImport.VALIDE,
          ActionLigneImport.MISE_A_JOUR,
          donnees,
          null,
          entiteId);
    } catch (RuntimeException e) {
      return ImportLigneResultat.erreur(numeroLigne, donnees, List.of(e.getMessage()));
    }
  }

  private static String valeur(Map<String, String> donnees, String cle) {
    String v = donnees.get(cle);
    return v == null ? null : v.trim();
  }
}
