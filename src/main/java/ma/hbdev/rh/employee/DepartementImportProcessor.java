package ma.hbdev.rh.employee;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traite une ligne de la cible {@link ImportCible#DEPARTEMENTS} (EF-EMP-07). */
@Component
class DepartementImportProcessor {

  private final DepartementRepository departementRepository;
  private final DepartementService departementService;

  DepartementImportProcessor(
      DepartementRepository departementRepository, DepartementService departementService) {
    this.departementRepository = departementRepository;
    this.departementService = departementService;
  }

  ImportLigneResultat traiter(
      int numeroLigne, Map<String, String> donnees, boolean dryRun, ImportSuiviLot suivi) {
    String nom = valeur(donnees, "nom");
    if (nom == null || nom.isBlank()) {
      return ImportLigneResultat.erreur(numeroLigne, donnees, List.of("Nom du département requis"));
    }
    if (nom.length() > 150) {
      return ImportLigneResultat.erreur(
          numeroLigne, donnees, List.of("Nom du département trop long (150 caractères max)"));
    }

    boolean dejaVuDansLot = !suivi.premiereFois("departement:" + nom);
    boolean existeEnBase = departementRepository.existsByNomIgnoreCase(nom);
    if (dejaVuDansLot || existeEnBase) {
      return new ImportLigneResultat(
          numeroLigne,
          StatutLigneImport.VALIDE,
          ActionLigneImport.AUCUN_CHANGEMENT,
          donnees,
          null,
          null);
    }

    // dryRun toujours exécuté (comme EmployeImportProcessor) : transaction REQUIRES_NEW annulée
    // après coup côté ImportService quand dryRun est vrai, rien n'est jamais persisté en
    // simulation — mais la simulation bénéficie des mêmes règles de validation qu'un import réel.
    UUID entiteId = departementService.creer(new DepartementRequete(nom, null)).getId();
    return new ImportLigneResultat(
        numeroLigne, StatutLigneImport.VALIDE, ActionLigneImport.CREATION, donnees, null, entiteId);
  }

  private static String valeur(Map<String, String> donnees, String cle) {
    String v = donnees.get(cle);
    return v == null ? null : v.trim();
  }
}
