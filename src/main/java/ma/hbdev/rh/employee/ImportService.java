package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Orchestrateur de l'import Excel/CSV (EF-EMP-07) — parsing, mapping, validation, exécution. */
@Service
class ImportService {

  private final TableurParserFactory parserFactory;
  private final DepartementImportProcessor departementProcessor;
  private final EmployeImportProcessor employeProcessor;
  private final SoldeCongeImportProcessor soldeProcessor;
  private final ImportLotRepository lotRepository;
  private final ImportLigneRepository ligneRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher evenements;
  private final TransactionTemplate transactionLigne;

  ImportService(
      TableurParserFactory parserFactory,
      DepartementImportProcessor departementProcessor,
      EmployeImportProcessor employeProcessor,
      SoldeCongeImportProcessor soldeProcessor,
      ImportLotRepository lotRepository,
      ImportLigneRepository ligneRepository,
      ObjectMapper objectMapper,
      ApplicationEventPublisher evenements,
      PlatformTransactionManager transactionManager) {
    this.parserFactory = parserFactory;
    this.departementProcessor = departementProcessor;
    this.employeProcessor = employeProcessor;
    this.soldeProcessor = soldeProcessor;
    this.lotRepository = lotRepository;
    this.ligneRepository = ligneRepository;
    this.objectMapper = objectMapper;
    this.evenements = evenements;
    // Chaque ligne s'exécute dans sa propre transaction REQUIRES_NEW plutôt que de partager celle
    // du lot (executerReellement) : si le service métier appelé (EmployeService/DepartementService,
    // eux-mêmes @Transactional) lève une exception métier (ex. e-mail déjà utilisé), Spring marque
    // la transaction *physique* rollback-only dès la sortie de ce service — même si traiterLigne()
    // catche l'exception juste après. Sans cette isolation, une seule ligne invalide fait échouer
    // tout le lot au commit final avec UnexpectedRollbackException (HTTP 500), alors que le but de
    // l'import ligne-par-ligne est justement de continuer malgré des lignes en erreur.
    this.transactionLigne = new TransactionTemplate(transactionManager);
    this.transactionLigne.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private static final int TAILLE_APERCU = 20;

  ImportApercuReponse previsualiser(MultipartFile fichier, ImportCible cible) {
    TableurBrut brut = parserFactory.parser(fichier);
    List<ImportChampSpec> champs = ImportChampsRegistry.champsPour(cible);
    Map<String, Integer> suggestion = ColonneMatcher.suggererMapping(brut.entetes(), champs);
    return new ImportApercuReponse(
        brut.entetes(),
        brut.lignes().stream().limit(TAILLE_APERCU).toList(),
        brut.lignes().size(),
        champs.stream().map(ImportChampSpecReponse::depuis).toList(),
        suggestion);
  }

  @Transactional
  ImportExecutionResultat analyser(
      MultipartFile fichier,
      ImportCible cible,
      Map<String, Integer> mapping,
      StrategieDoublon strategieDoublon) {
    return executer(fichier, cible, mapping, true, strategieDoublon);
  }

  @Transactional
  ImportExecutionResultat executerReellement(
      MultipartFile fichier,
      ImportCible cible,
      Map<String, Integer> mapping,
      StrategieDoublon strategieDoublon) {
    return executer(fichier, cible, mapping, false, strategieDoublon);
  }

  private ImportExecutionResultat executer(
      MultipartFile fichier,
      ImportCible cible,
      Map<String, Integer> mapping,
      boolean dryRun,
      StrategieDoublon strategieDoublon) {
    validerMapping(cible, mapping);
    TableurBrut brut = parserFactory.parser(fichier);
    ImportSuiviLot suivi = new ImportSuiviLot();

    List<ImportLigneResultat> resultats = new ArrayList<>();
    for (int i = 0; i < brut.lignes().size(); i++) {
      int numeroLigne = i + 1;
      Map<String, String> donnees = extraireDonnees(brut.lignes().get(i), mapping);
      resultats.add(
          traiterLigneIsolee(cible, numeroLigne, donnees, dryRun, suivi, strategieDoublon));
    }

    int lignesValides =
        (int) resultats.stream().filter(r -> r.statut() != StatutLigneImport.ERREUR).count();
    int lignesErreur = resultats.size() - lignesValides;

    ImportLot lot =
        lotRepository.save(
            new ImportLot(
                cible,
                dryRun ? ModeImportLot.SIMULATION : ModeImportLot.REEL,
                fichier.getOriginalFilename(),
                resultats.size(),
                lignesValides,
                lignesErreur,
                CurrentUser.id().orElse(null)));

    ligneRepository.saveAll(
        resultats.stream()
            .map(
                r ->
                    new ImportLigne(
                        lot.getId(),
                        r.numeroLigne(),
                        r.statut(),
                        r.action(),
                        objectMapper.valueToTree(r.donnees()),
                        r.erreurs() == null || r.erreurs().isEmpty()
                            ? null
                            : String.join("; ", r.erreurs()),
                        r.entiteId()))
            .toList());

    if (!dryRun) {
      evenements.publishEvent(new ImportExecuteEvent(lot.getId(), cible.name()));
    }

    return new ImportExecutionResultat(
        lot.getId(),
        cible,
        lot.getMode(),
        resultats.size(),
        lignesValides,
        lignesErreur,
        resultats);
  }

  // cf. commentaire sur transactionLigne (constructeur) : isole chaque ligne dans sa propre
  // transaction REQUIRES_NEW pour qu'une ligne en erreur ne poisonne pas la transaction du lot.
  private ImportLigneResultat traiterLigneIsolee(
      ImportCible cible,
      int numeroLigne,
      Map<String, String> donnees,
      boolean dryRun,
      ImportSuiviLot suivi,
      StrategieDoublon strategieDoublon) {
    // traiterLigne() catche déjà l'exception métier d'origine (ex. "e-mail déjà utilisé") et
    // construit un résultat ERREUR avec le vrai message — mais la transaction REQUIRES_NEW peut
    // avoir été marquée rollback-only par le proxy @Transactional du service métier *avant* ce
    // catch, donc son commit échoue quand même juste après que le callback a retourné normalement,
    // avec UnexpectedRollbackException (message générique, sans rapport avec la cause réelle). On
    // capture donc le résultat déjà calculé pour le réutiliser dans ce cas plutôt que d'afficher le
    // message de plomberie transactionnelle.
    AtomicReference<ImportLigneResultat> calcule = new AtomicReference<>();
    try {
      return transactionLigne.execute(
          status -> {
            ImportLigneResultat resultat =
                traiterLigne(cible, numeroLigne, donnees, dryRun, suivi, strategieDoublon);
            // Vrai dry-run : les processors écrivent réellement (mêmes règles métier qu'un import
            // réel, cf. EmployeService/DepartementService/MouvementCongeRepository), mais cette
            // transaction REQUIRES_NEW est annulée systématiquement pour qu'aucune donnée ne soit
            // persistée. setRollbackOnly() ici (plutôt qu'une exception) est le mécanisme normal —
            // TransactionTemplate ne lève pas UnexpectedRollbackException dans ce cas : ça ne
            // concerne que le rollback *inattendu*, provoqué par un participant imbriqué à l'insu
            // de l'appelant (cf. commentaire au-dessus pour ce cas-là).
            if (dryRun) {
              status.setRollbackOnly();
            }
            calcule.set(resultat);
            return resultat;
          });
    } catch (RuntimeException e) {
      ImportLigneResultat dejaCalcule = calcule.get();
      if (dejaCalcule != null && dejaCalcule.statut() == StatutLigneImport.ERREUR) {
        return dejaCalcule;
      }
      return ImportLigneResultat.erreur(
          numeroLigne,
          donnees,
          List.of("Échec de l'enregistrement de cette ligne : " + e.getMessage()));
    }
  }

  private ImportLigneResultat traiterLigne(
      ImportCible cible,
      int numeroLigne,
      Map<String, String> donnees,
      boolean dryRun,
      ImportSuiviLot suivi,
      StrategieDoublon strategieDoublon) {
    try {
      return switch (cible) {
        case DEPARTEMENTS -> departementProcessor.traiter(numeroLigne, donnees, dryRun, suivi);
        case EMPLOYES ->
            employeProcessor.traiter(numeroLigne, donnees, dryRun, suivi, strategieDoublon);
        case SOLDES_CONGES_INITIAUX ->
            soldeProcessor.traiter(numeroLigne, donnees, dryRun, suivi, strategieDoublon);
      };
    } catch (RuntimeException e) {
      return ImportLigneResultat.erreur(
          numeroLigne, donnees, List.of(String.valueOf(e.getMessage())));
    }
  }

  private Map<String, String> extraireDonnees(List<String> ligne, Map<String, Integer> mapping) {
    Map<String, String> donnees = new LinkedHashMap<>();
    mapping.forEach(
        (champ, colonne) -> donnees.put(champ, colonne < ligne.size() ? ligne.get(colonne) : null));
    return donnees;
  }

  private void validerMapping(ImportCible cible, Map<String, Integer> mapping) {
    List<String> manquants =
        ImportChampsRegistry.champsPour(cible).stream()
            .filter(ImportChampSpec::requis)
            .map(ImportChampSpec::cle)
            .filter(cle -> !mapping.containsKey(cle))
            .toList();
    if (!manquants.isEmpty()) {
      throw new ImportMappingInvalideException(
          "Champs requis non mappés à une colonne : " + String.join(", ", manquants));
    }
  }

  @Transactional(readOnly = true)
  Page<ImportLot> historique(Pageable pageable) {
    return lotRepository.findAllByOrderByCreeLeDesc(pageable);
  }

  @Transactional(readOnly = true)
  ImportLot trouverLot(UUID lotId) {
    return lotRepository
        .findById(lotId)
        .orElseThrow(() -> new ImportLotIntrouvableException(lotId));
  }

  @Transactional(readOnly = true)
  List<ImportLigne> lignesDuLot(UUID lotId) {
    return ligneRepository.findByLotIdOrderByNumeroLigne(lotId);
  }
}
