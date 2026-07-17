package ma.hbdev.rh.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.ai.AnalyseResultat;
import ma.hbdev.rh.shared.ai.ContexteOffre;
import ma.hbdev.rh.shared.ai.CvAnalysisException;
import ma.hbdev.rh.shared.ai.CvAnalysisProvider;
import ma.hbdev.rh.shared.file.FichierInvalideException;
import ma.hbdev.rh.shared.file.FichierUploade;
import ma.hbdev.rh.shared.file.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * EF-REC-02/03/04/05/11 : point d'entrée de l'ingestion e-mail (le workflow n8n IMAP transfère
 * l'e-mail brut ici, toute la normalisation/logique métier reste dans le backend — ai-
 * instructions.md règle 7). Aussi utilisé pour la relance manuelle d'analyse (EF-REC-10).
 */
@Service
@Transactional
@Slf4j
class CandidatureIngestionService {

  private final CandidatureRepository candidatureRepository;
  private final OffreEmploiRepository offreEmploiRepository;
  private final AnalyseIaRepository analyseIaRepository;
  private final FileStorageService fileStorageService;
  private final CvAnalysisProvider cvAnalysisProvider;
  private final ApplicationEventPublisher evenements;
  private final ObjectMapper objectMapper;

  CandidatureIngestionService(
      CandidatureRepository candidatureRepository,
      OffreEmploiRepository offreEmploiRepository,
      AnalyseIaRepository analyseIaRepository,
      FileStorageService fileStorageService,
      CvAnalysisProvider cvAnalysisProvider,
      ApplicationEventPublisher evenements,
      ObjectMapper objectMapper) {
    this.candidatureRepository = candidatureRepository;
    this.offreEmploiRepository = offreEmploiRepository;
    this.analyseIaRepository = analyseIaRepository;
    this.fileStorageService = fileStorageService;
    this.cvAnalysisProvider = cvAnalysisProvider;
    this.evenements = evenements;
    this.objectMapper = objectMapper;
  }

  Candidature ingerer(
      String emailExpediteur,
      String nomExpediteur,
      String sujet,
      String corps,
      String referenceSourceImport,
      MultipartFile cv) {
    OffreEmploi offreCorrespondante = resoudreOffre(sujet, corps);

    // EF-REC-11 : une candidature reçue pour une offre fermée reste "en_attente", pas rejetée.
    StatutCandidature statutInitial =
        (offreCorrespondante != null && offreCorrespondante.getStatut() == StatutOffreEmploi.fermee)
            ? StatutCandidature.en_attente
            : StatutCandidature.recu;

    UUID offreId = offreCorrespondante != null ? offreCorrespondante.getId() : null;

    // Ré-ingestion sur une offre déjà candidatée par cet e-mail : on met à jour plutôt que de
    // heurter la contrainte UNIQUE(offre_id, email) avec une exception non gérée.
    if (offreId != null) {
      var existante = candidatureRepository.findByOffreIdAndEmail(offreId, emailExpediteur);
      if (existante.isPresent()) {
        Candidature candidature = existante.get();
        if (cv != null && !cv.isEmpty()) {
          FichierUploade fichier = fileStorageService.televerser(cv, null);
          declencherAnalyse(
              candidature, fichier, offreCorrespondante, candidature.getAnalyseCouranteId());
        }
        evenements.publishEvent(
            new CandidatureModifieEvent(candidature.getId(), "reception_dupliquee"));
        // findByOffreIdAndEmail ne fait pas de JOIN FETCH sur analyseCourante : sans ce
        // re-chargement, le proxy Hibernate créé au premier chargement (pointant vers l'ancienne
        // analyse) casse la sérialisation de la réponse une fois la transaction/session fermée
        // (LazyInitializationException), même si `analyseCouranteId` a bien été mis à jour en base.
        return candidatureRepository.findByIdAvecAnalyseCourante(candidature.getId()).orElseThrow();
      }
    }

    FichierUploade fichier = null;
    if (cv != null && !cv.isEmpty()) {
      try {
        fichier = fileStorageService.televerser(cv, null);
      } catch (FichierInvalideException e) {
        log.warn("CV rejeté à l'ingestion pour {} : {}", emailExpediteur, e.getMessage());
      }
    }

    // EF-REC-05 : filet de sécurité si l'analyse IA échoue/est indisponible — sans ça, prenom/nom
    // restent null tant que l'IA n'a jamais tourné avec succès, même si l'e-mail portait un nom
    // d'expéditeur exploitable ("Harold Gavi <harold@...>"). Best-effort seulement (l'IA, plus
    // fiable car lue depuis le CV, garde la priorité si elle réussit ensuite — cf.
    // completerIdentiteSiAbsente, qui ne remplace jamais un champ déjà renseigné).
    String[] nomPrenom = scinderNomExpediteur(nomExpediteur);

    Candidature candidature =
        candidatureRepository.save(
            new Candidature(
                offreId,
                nomPrenom[1],
                nomPrenom[0],
                emailExpediteur,
                null,
                null,
                SourceCandidature.email,
                fichier != null ? fichier.id() : null,
                statutInitial,
                referenceSourceImport));

    evenements.publishEvent(new CandidatureModifieEvent(candidature.getId(), "ingestion"));
    declencherAnalyse(candidature, fichier, offreCorrespondante, null);
    return candidature;
  }

  // EF-REC-10 : relance manuelle, chaînée via remplace_analyse_id (l'historique est conservé).
  void relancerAnalyse(UUID candidatureId) {
    Candidature candidature = trouver(candidatureId);
    FichierUploade fichier =
        candidature.getCvFichierId() != null
            ? fileStorageService.recuperer(candidature.getCvFichierId())
            : null;
    OffreEmploi offre =
        candidature.getOffreId() != null
            ? offreEmploiRepository.findById(candidature.getOffreId()).orElse(null)
            : null;
    declencherAnalyse(candidature, fichier, offre, candidature.getAnalyseCouranteId());
    evenements.publishEvent(new CandidatureModifieEvent(candidature.getId(), "relance_analyse"));
  }

  private void declencherAnalyse(
      Candidature candidature,
      FichierUploade fichier,
      OffreEmploi offre,
      UUID analysePrecedenteId) {
    if (fichier == null || !cvAnalysisProvider.disponible()) {
      AnalyseIa analyse =
          analyseIaRepository.save(
              new AnalyseIa(
                  candidature.getId(),
                  StatutAnalyseIa.en_attente,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  analysePrecedenteId));
      candidature.definirAnalyseCourante(analyse.getId());
      return;
    }
    try {
      Resource ressourceCv = fileStorageService.charger(fichier.id());
      ContexteOffre contexte =
          offre != null
              ? new ContexteOffre(
                  offre.getIntitule(),
                  offre.getDescription(),
                  MotsClesUtils.versListe(offre.getMotsClesRequis()))
              : null;
      AnalyseResultat resultat =
          cvAnalysisProvider.analyser(ressourceCv, fichier.typeMime(), contexte);
      AnalyseIa analyse =
          analyseIaRepository.save(
              new AnalyseIa(
                  candidature.getId(),
                  StatutAnalyseIa.succes,
                  resultat.prenom(),
                  resultat.nom(),
                  resultat.email(),
                  resultat.telephone(),
                  resultat.intitulePoste(),
                  resultat.scoreCorrespondance(),
                  resultat.anneesExperienceEstimees(),
                  resultat.justificationScore(),
                  MotsClesUtils.versJsonNode(resultat.motsCles(), objectMapper),
                  analysePrecedenteId));
      candidature.definirAnalyseCourante(analyse.getId());
      // EF-EMP-05 : réutilisé plus tard pour préremplir la fiche employé en cas d'embauche.
      candidature.completerIdentiteSiAbsente(
          resultat.prenom(),
          resultat.nom(),
          resultat.email(),
          resultat.telephone(),
          resultat.intitulePoste());
    } catch (CvAnalysisException e) {
      log.warn("Analyse IA échouée pour la candidature {}", candidature.getId(), e);
      AnalyseIa analyse =
          analyseIaRepository.save(
              new AnalyseIa(
                  candidature.getId(),
                  StatutAnalyseIa.echec,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  analysePrecedenteId));
      candidature.definirAnalyseCourante(analyse.getId());
    }
  }

  // Hypothèse à valider avec Taha une fois des e-mails réels reçus (cf. plan d'implémentation) :
  // correspondance floue du sujet/corps du mail contre les intitulés d'offres existantes ; aucune
  // correspondance -> offre_id laissé null, assignable manuellement depuis la fiche candidature.
  private OffreEmploi resoudreOffre(String sujet, String corps) {
    String texte =
        ((sujet == null ? "" : sujet) + " " + (corps == null ? "" : corps)).toLowerCase();
    if (texte.isBlank()) {
      return null;
    }
    List<OffreEmploi> toutes = offreEmploiRepository.findAll();
    return toutes.stream()
        .filter(o -> o.getIntitule() != null && texte.contains(o.getIntitule().toLowerCase()))
        .min(Comparator.comparing(o -> o.getStatut() == StatutOffreEmploi.fermee))
        .orElse(null);
  }

  // Heuristique simple : dernier mot = nom, le reste = prénom (ex. "Harold Gavi" -> ["Harold",
  // "Gavi"]). Best-effort, l'Admin révise toujours avant de créer la fiche employé (EF-EMP-05).
  private static String[] scinderNomExpediteur(String nomExpediteur) {
    if (nomExpediteur == null || nomExpediteur.isBlank()) {
      return new String[] {null, null};
    }
    String[] mots = nomExpediteur.trim().split("\\s+");
    if (mots.length == 1) {
      return new String[] {mots[0], null};
    }
    String nom = mots[mots.length - 1];
    String prenom = String.join(" ", Arrays.copyOf(mots, mots.length - 1));
    return new String[] {prenom, nom};
  }

  private Candidature trouver(UUID id) {
    return candidatureRepository
        .findById(id)
        .orElseThrow(() -> new CandidatureIntrouvableException(id));
  }
}
