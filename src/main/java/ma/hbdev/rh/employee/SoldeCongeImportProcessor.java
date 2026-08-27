package ma.hbdev.rh.employee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Traite une ligne de la cible {@link ImportCible#SOLDES_CONGES_INITIAUX} (EF-EMP-07). Un seul
 * mouvement {@code initialisation} par employé : un ré-import corrige ce mouvement (mise à jour),
 * il n'en ajoute jamais un second — le solde de congés est toujours la somme du ledger {@code
 * mouvements_conges} (jamais stocké), une double "initialisation" fausserait le calcul.
 */
@Component
class SoldeCongeImportProcessor {

  private final EmployeRepository employeRepository;
  private final MouvementCongeRepository mouvementCongeRepository;

  SoldeCongeImportProcessor(
      EmployeRepository employeRepository, MouvementCongeRepository mouvementCongeRepository) {
    this.employeRepository = employeRepository;
    this.mouvementCongeRepository = mouvementCongeRepository;
  }

  ImportLigneResultat traiter(
      int numeroLigne,
      Map<String, String> donnees,
      boolean dryRun,
      ImportSuiviLot suivi,
      StrategieDoublon strategieDoublon) {
    String email = valeur(donnees, "employeEmail");
    String commentaire = valeur(donnees, "commentaire");
    BigDecimal quantiteJours = ImportParseUtils.parserNombre(valeur(donnees, "quantiteJours"));

    List<String> erreurs = new ArrayList<>();
    if (email == null || email.isBlank()) {
      erreurs.add("E-mail de l'employé requis");
    }
    if (valeur(donnees, "quantiteJours") == null || valeur(donnees, "quantiteJours").isBlank()) {
      erreurs.add("Solde initial requis");
    } else if (quantiteJours == null) {
      erreurs.add("Solde initial : format numérique non reconnu");
    } else if (quantiteJours.signum() < 0) {
      erreurs.add("Le solde initial ne peut pas être négatif");
    }

    Employe employe = null;
    if (email != null && !email.isBlank()) {
      employe = employeRepository.findByEmailIgnoreCase(email).orElse(null);
      if (employe == null) {
        erreurs.add(
            "Employé introuvable pour l'e-mail \"" + email + "\" (importer les employés d'abord)");
      }
    }

    if (!erreurs.isEmpty()) {
      return ImportLigneResultat.erreur(numeroLigne, donnees, erreurs);
    }

    if (!suivi.premiereFois("solde:" + email)) {
      return new ImportLigneResultat(
          numeroLigne,
          StatutLigneImport.AVERTISSEMENT,
          ActionLigneImport.IGNOREE,
          donnees,
          List.of("Solde déjà traité pour cet employé dans ce fichier — ligne ignorée"),
          null);
    }

    var existant =
        mouvementCongeRepository
            .findByEmployeIdAndTypeMouvement(employe.getId(), TypeMouvementConge.initialisation)
            .orElse(null);

    UUID creePar = CurrentUser.id().orElse(null);
    if (existant != null) {
      if (strategieDoublon == StrategieDoublon.IGNORER) {
        return new ImportLigneResultat(
            numeroLigne,
            StatutLigneImport.AVERTISSEMENT,
            ActionLigneImport.IGNOREE,
            donnees,
            List.of("Solde initial déjà enregistré pour cet employé — ligne ignorée"),
            existant.getId());
      }
      // dryRun toujours exécuté (comme EmployeImportProcessor) : transaction REQUIRES_NEW annulée
      // après coup côté ImportService quand dryRun est vrai.
      existant.mettreAJour(quantiteJours, commentaire);
      return new ImportLigneResultat(
          numeroLigne,
          StatutLigneImport.VALIDE,
          ActionLigneImport.MISE_A_JOUR,
          donnees,
          null,
          existant.getId());
    }

    MouvementConge cree =
        mouvementCongeRepository.save(
            new MouvementConge(
                employe.getId(),
                TypeMouvementConge.initialisation,
                quantiteJours,
                commentaire,
                creePar));
    UUID entiteId = cree.getId();
    return new ImportLigneResultat(
        numeroLigne, StatutLigneImport.VALIDE, ActionLigneImport.CREATION, donnees, null, entiteId);
  }

  private static String valeur(Map<String, String> donnees, String cle) {
    String v = donnees.get(cle);
    return v == null ? null : v.trim();
  }
}
