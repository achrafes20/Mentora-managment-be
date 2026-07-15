package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  ImportService(
      TableurParserFactory parserFactory,
      DepartementImportProcessor departementProcessor,
      EmployeImportProcessor employeProcessor,
      SoldeCongeImportProcessor soldeProcessor,
      ImportLotRepository lotRepository,
      ImportLigneRepository ligneRepository,
      ObjectMapper objectMapper,
      ApplicationEventPublisher evenements) {
    this.parserFactory = parserFactory;
    this.departementProcessor = departementProcessor;
    this.employeProcessor = employeProcessor;
    this.soldeProcessor = soldeProcessor;
    this.lotRepository = lotRepository;
    this.ligneRepository = ligneRepository;
    this.objectMapper = objectMapper;
    this.evenements = evenements;
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
      MultipartFile fichier, ImportCible cible, Map<String, Integer> mapping) {
    return executer(fichier, cible, mapping, true);
  }

  @Transactional
  ImportExecutionResultat executerReellement(
      MultipartFile fichier, ImportCible cible, Map<String, Integer> mapping) {
    return executer(fichier, cible, mapping, false);
  }

  private ImportExecutionResultat executer(
      MultipartFile fichier, ImportCible cible, Map<String, Integer> mapping, boolean dryRun) {
    validerMapping(cible, mapping);
    TableurBrut brut = parserFactory.parser(fichier);
    ImportSuiviLot suivi = new ImportSuiviLot();

    List<ImportLigneResultat> resultats = new ArrayList<>();
    for (int i = 0; i < brut.lignes().size(); i++) {
      int numeroLigne = i + 1;
      Map<String, String> donnees = extraireDonnees(brut.lignes().get(i), mapping);
      resultats.add(traiterLigne(cible, numeroLigne, donnees, dryRun, suivi));
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

  private ImportLigneResultat traiterLigne(
      ImportCible cible,
      int numeroLigne,
      Map<String, String> donnees,
      boolean dryRun,
      ImportSuiviLot suivi) {
    try {
      return switch (cible) {
        case DEPARTEMENTS -> departementProcessor.traiter(numeroLigne, donnees, dryRun, suivi);
        case EMPLOYES -> employeProcessor.traiter(numeroLigne, donnees, dryRun, suivi);
        case SOLDES_CONGES_INITIAUX -> soldeProcessor.traiter(numeroLigne, donnees, dryRun, suivi);
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
