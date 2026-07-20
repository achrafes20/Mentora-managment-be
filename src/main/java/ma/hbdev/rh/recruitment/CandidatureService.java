package ma.hbdev.rh.recruitment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.hbdev.rh.shared.config.ConfigurationService;
import ma.hbdev.rh.shared.file.FichierUploade;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EF-REC-06/07/11/12/14 : consultation, pipeline de statuts, réactivation, archivage. */
@Service
@Transactional
class CandidatureService {

  private static final String CORPS_REJET_DEFAUT =
      "Bonjour,\n\nNous vous remercions pour l'intérêt porté à notre entreprise et pour le temps "
          + "consacré à votre candidature. Après étude attentive de votre profil, nous ne "
          + "donnerons malheureusement pas suite à celle-ci pour ce poste.\n\nNous vous "
          + "souhaitons une pleine réussite dans vos recherches.\n\nCordialement,\nL'équipe "
          + "recrutement HB Développement";

  // EF-REC-07 : Reçu -> Présélectionné -> Entretien -> Décision -> Embauché/Rejeté, plus les
  // statuts transversaux "En attente"/"Suggestion de réactivation" (EF-REC-11/12).
  // Entretien -> Décision n'est PAS une transition manuelle : elle se déclenche uniquement quand
  // le Manager rend son résultat (cf. avancerVersDecisionDepuisEntretien, appelée par
  // EntretienService) — l'Admin ne peut que rejeter ou reprogrammer pendant l'entretien (décision
  // confirmée avec Taha).
  private static final Map<StatutCandidature, Set<StatutCandidature>> TRANSITIONS_AUTORISEES =
      Map.of(
          StatutCandidature.recu,
              Set.of(StatutCandidature.preselectionne, StatutCandidature.rejete),
          StatutCandidature.preselectionne,
              Set.of(StatutCandidature.entretien, StatutCandidature.rejete),
          StatutCandidature.entretien, Set.of(StatutCandidature.rejete),
          StatutCandidature.decision, Set.of(StatutCandidature.embauche, StatutCandidature.rejete),
          StatutCandidature.suggestion_reactivation,
              Set.of(StatutCandidature.recu, StatutCandidature.archivee),
          StatutCandidature.en_attente, Set.of(StatutCandidature.archivee));

  private final CandidatureRepository candidatureRepository;
  private final EntretienRepository entretienRepository;
  private final EnvoiDocumentRepository envoiDocumentRepository;
  private final ConfigurationService configurationService;
  private final MailService mailService;
  private final FileStorageService fileStorageService;
  private final ApplicationEventPublisher evenements;

  CandidatureService(
      CandidatureRepository candidatureRepository,
      EntretienRepository entretienRepository,
      EnvoiDocumentRepository envoiDocumentRepository,
      ConfigurationService configurationService,
      MailService mailService,
      FileStorageService fileStorageService,
      ApplicationEventPublisher evenements) {
    this.candidatureRepository = candidatureRepository;
    this.entretienRepository = entretienRepository;
    this.envoiDocumentRepository = envoiDocumentRepository;
    this.configurationService = configurationService;
    this.mailService = mailService;
    this.fileStorageService = fileStorageService;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  Page<Candidature> lister(
      UUID offreId,
      StatutCandidature statut,
      BigDecimal scoreMin,
      String recherche,
      Pageable pageable) {
    var spec = CandidatureSpecifications.filtrer(offreId, statut, scoreMin, recherche);
    if (CurrentUser.hasRole("MANAGER") && !CurrentUser.hasRole("ADMIN")) {
      UUID managerId =
          CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifié"));
      spec = spec.and(CandidatureSpecifications.pourManager(managerId));
    }
    return candidatureRepository.findAll(spec, pageable);
  }

  @Transactional(readOnly = true)
  Candidature trouver(UUID id) {
    Candidature candidature =
        candidatureRepository
            .findByIdAvecAnalyseCourante(id)
            .orElseThrow(() -> new CandidatureIntrouvableException(id));
    verifierPerimetreManager(candidature);
    return candidature;
  }

  // EF-REC-08 : le Manager n'a accès qu'aux candidatures pour lesquelles un entretien lui a été
  // assigné (même principe que EmployeService.verifierPerimetreManager pour EF-AUTH-03).
  private void verifierPerimetreManager(Candidature candidature) {
    if (!CurrentUser.hasRole("MANAGER") || CurrentUser.hasRole("ADMIN")) {
      return;
    }
    UUID managerId =
        CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifié"));
    boolean assigne =
        entretienRepository.findByCandidatureIdOrderByCreeLeDesc(candidature.getId()).stream()
            .anyMatch(e -> managerId.equals(e.getManagerId()));
    if (!assigne) {
      throw new AccessDeniedException("Candidature hors du périmètre du Manager");
    }
  }

  Candidature changerStatut(
      UUID id,
      StatutCandidature nouveauStatut,
      UUID managerId,
      Instant dateEntretien,
      String corpsMessage) {
    Candidature candidature = trouver(id);
    validerTransition(candidature.getStatut(), nouveauStatut);
    candidature.changerStatut(nouveauStatut);
    evenements.publishEvent(
        new CandidatureModifieEvent(candidature.getId(), "statut_" + nouveauStatut));

    if (nouveauStatut == StatutCandidature.entretien) {
      demarrerEntretien(candidature, managerId, dateEntretien);
    } else if (nouveauStatut == StatutCandidature.rejete) {
      envoyerRejet(candidature, corpsMessage);
    }
    return candidature;
  }

  // EF-REC-09 : reprogrammation (manager et/ou date) tant qu'aucun résultat n'a été rendu — seule
  // action de l'Admin autorisée pendant l'étape Entretien, en dehors du rejet.
  Entretien reprogrammerEntretien(UUID candidatureId, UUID nouveauManagerId, Instant nouvelleDate) {
    Candidature candidature = trouver(candidatureId);
    if (candidature.getStatut() != StatutCandidature.entretien) {
      throw new TransitionCandidatureInvalideException(
          candidature.getStatut(), StatutCandidature.entretien);
    }
    Entretien entretien =
        entretienRepository.findByCandidatureIdOrderByCreeLeDesc(candidatureId).stream()
            .findFirst()
            .orElseThrow(() -> new EntretienIntrouvableException(candidatureId));
    if (entretien.resultatDejaRendu()) {
      throw new EntretienDejaResoluException(candidatureId);
    }
    entretien.reprogrammer(nouveauManagerId, nouvelleDate);
    evenements.publishEvent(new CandidatureModifieEvent(candidatureId, "entretien_reprogramme"));
    return entretien;
  }

  // Appelée par EntretienService juste après l'enregistrement du résultat — seule voie vers
  // "decision" depuis "entretien" (pas de transition manuelle équivalente, cf.
  // TRANSITIONS_AUTORISEES).
  void avancerVersDecisionDepuisEntretien(UUID candidatureId) {
    Candidature candidature = trouver(candidatureId);
    if (candidature.getStatut() != StatutCandidature.entretien) {
      return;
    }
    candidature.changerStatut(StatutCandidature.decision);
    evenements.publishEvent(new CandidatureModifieEvent(candidatureId, "statut_decision_auto"));
  }

  @Transactional(readOnly = true)
  CandidatureCvTelecharge telechargerCv(UUID id) {
    Candidature candidature = trouver(id);
    if (candidature.getCvFichierId() == null) {
      throw new CandidatureCvIntrouvableException(id);
    }
    FichierUploade fichier = fileStorageService.recuperer(candidature.getCvFichierId());
    return new CandidatureCvTelecharge(
        fileStorageService.charger(fichier.id()), fichier.nomOriginal(), fichier.typeMime());
  }

  Candidature validerReactivation(UUID id) {
    Candidature candidature = trouver(id);
    if (candidature.getStatut() != StatutCandidature.suggestion_reactivation) {
      throw new TransitionCandidatureInvalideException(
          candidature.getStatut(), StatutCandidature.recu);
    }
    candidature.changerStatut(StatutCandidature.recu);
    evenements.publishEvent(
        new CandidatureModifieEvent(candidature.getId(), "reactivation_validee"));
    return candidature;
  }

  // EF-REC-12 (fenêtre de rétention dépassée) : n8n cron ping, cf. n8n/README.md.
  int archiverExpirees() {
    int fenetreMois = configurationService.getInteger("fenetre_retention_candidature_mois", 6);
    Instant seuil = Instant.now().minus(fenetreMois * 30L, ChronoUnit.DAYS);
    List<Candidature> expirees =
        candidatureRepository.findByStatutAndDateIngestionBefore(
            StatutCandidature.en_attente, seuil);
    expirees.forEach(Candidature::archiver);
    expirees.forEach(
        c -> evenements.publishEvent(new CandidatureModifieEvent(c.getId(), "archivage_auto")));
    return expirees.size();
  }

  private void validerTransition(StatutCandidature actuel, StatutCandidature demande) {
    Set<StatutCandidature> autorisees = TRANSITIONS_AUTORISEES.get(actuel);
    if (autorisees == null || !autorisees.contains(demande)) {
      throw new TransitionCandidatureInvalideException(actuel, demande);
    }
  }

  // EF-REC-08 : le Manager assigné est celui choisi côté Admin (résolu côté frontend depuis
  // GET /api/departements, déjà public — évite un appel direct au module employé, cf.
  // ai-instructions.md règle 4). Obligatoire : sans lui, aucune ligne `entretiens` ne peut exister
  // (manager_id NOT NULL) et la candidature resterait bloquée en "Entretien" sans jamais apparaître
  // dans la file d'aucun Manager (cf. CandidatureSpecifications.pourManager).
  private void demarrerEntretien(Candidature candidature, UUID managerId, Instant dateEntretien) {
    if (managerId == null) {
      throw new ManagerRequisPourEntretienException();
    }
    entretienRepository.save(new Entretien(candidature.getId(), managerId, dateEntretien));
    String nomComplet =
        ((candidature.getPrenom() == null ? "" : candidature.getPrenom())
                + " "
                + (candidature.getNom() == null ? "" : candidature.getNom()))
            .trim();
    evenements.publishEvent(
        CandidatureEntretienEvent.pourCandidat(candidature.getId(), managerId, nomComplet));
  }

  // EF-REC-14 : e-mail de rejet, corps standard éditable par l'Admin avant envoi, journalisé
  // comme un envoi de document RH (EF-DOC-06).
  private void envoyerRejet(Candidature candidature, String corpsMessagePersonnalise) {
    String corps =
        (corpsMessagePersonnalise != null && !corpsMessagePersonnalise.isBlank())
            ? corpsMessagePersonnalise
            : CORPS_REJET_DEFAUT;
    mailService.sendEmail(candidature.getEmail(), "Suite à votre candidature", corps);
    envoiDocumentRepository.save(
        new EnvoiDocument(
            candidature.getId(),
            TypeDocumentRh.email_rejet_candidature,
            candidature.getEmail(),
            corps,
            CurrentUser.id().orElse(null)));
  }
}
