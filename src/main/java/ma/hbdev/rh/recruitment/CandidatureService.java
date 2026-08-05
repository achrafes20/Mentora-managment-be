package ma.hbdev.rh.recruitment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.hbdev.rh.auth.DelegationService;
import ma.hbdev.rh.shared.file.FichierUploade;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
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
  private final MailService mailService;
  private final FileStorageService fileStorageService;
  private final ApplicationEventPublisher evenements;
  private final DelegationService delegationService;

  // EF-REC-12 : constante technique fixée au déploiement (cf. décision T4.B2 du 2026-07-24).
  private final int fenetreRetentionMois;

  CandidatureService(
      CandidatureRepository candidatureRepository,
      EntretienRepository entretienRepository,
      EnvoiDocumentRepository envoiDocumentRepository,
      MailService mailService,
      FileStorageService fileStorageService,
      ApplicationEventPublisher evenements,
      DelegationService delegationService,
      @Value("${app.recruitment.reactivation.fenetre-mois:6}") int fenetreRetentionMois) {
    this.candidatureRepository = candidatureRepository;
    this.entretienRepository = entretienRepository;
    this.envoiDocumentRepository = envoiDocumentRepository;
    this.mailService = mailService;
    this.fileStorageService = fileStorageService;
    this.evenements = evenements;
    this.delegationService = delegationService;
    this.fenetreRetentionMois = fenetreRetentionMois;
  }

  @Transactional(readOnly = true)
  Page<Candidature> lister(
      UUID offreId,
      StatutCandidature statut,
      BigDecimal scoreMin,
      String recherche,
      Pageable pageable) {
    var spec = CandidatureSpecifications.filtrer(offreId, statut, scoreMin, recherche);
    if (CurrentUser.hasRole("MANAGER")
        && !CurrentUser.hasRole("ADMIN")
        && !delegationService.estDelegueActif()) {
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
  // assigné (même principe que EmployeService.verifierPerimetreManager pour EF-AUTH-03) — sauf
  // délégué actif (EF-AUTH-11/12), qui doit voir tout le vivier pour exercer ses droits de
  // décision (cf. lister() ci-dessus, même garde).
  private void verifierPerimetreManager(Candidature candidature) {
    if (!CurrentUser.hasRole("MANAGER")
        || CurrentUser.hasRole("ADMIN")
        || delegationService.estDelegueActif()) {
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
        new CandidatureModifieEvent(
            candidature.getId(), "statut_" + nouveauStatut, candidature.nomComplet()));

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
    evenements.publishEvent(
        new CandidatureModifieEvent(
            candidatureId, "entretien_reprogramme", candidature.nomComplet()));
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
    evenements.publishEvent(
        new CandidatureModifieEvent(
            candidatureId, "statut_decision_auto", candidature.nomComplet()));
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
        new CandidatureModifieEvent(
            candidature.getId(), "reactivation_validee", candidature.nomComplet()));
    return candidature;
  }

  /**
   * EF-REC-12 : déclencheur automatique de l'archivage, une fois par jour.
   *
   * <p>{@code @Scheduled} Spring plutôt qu'un ping cron n8n — rejoint le mécanisme déjà majoritaire
   * dans le backend ({@code AnomalieService}, {@code DelegationService}, {@code
   * NotificationService}, {@code SurveillancePlanifieeService}) et évite qu'une règle métier
   * dépende de la disponibilité de n8n (ai-instructions.md règle 7, révisée en conséquence).
   * L'endpoint manuel Admin ({@code CandidatureController#archiverExpirees}) reste disponible pour
   * les rejeux.
   *
   * <p>{@code zone} explicite (Africa/Casablanca) : même motif que {@link
   * ma.hbdev.rh.document.SurveillancePlanifieeService#balayageQuotidien()} — le Maroc suspend
   * l'heure d'été pendant le Ramadan, on ne se repose jamais sur le fuseau par défaut de la JVM.
   */
  @Scheduled(cron = "${app.recruitment.archivage-cron:0 30 3 * * *}", zone = "Africa/Casablanca")
  void archivageQuotidien() {
    archiverExpirees();
  }

  // EF-REC-12 (fenêtre de rétention dépassée) : appelée par le cron ci-dessus et par le
  // déclenchement manuel Admin (CandidatureController).
  int archiverExpirees() {
    Instant seuil = Instant.now().minus(fenetreRetentionMois * 30L, ChronoUnit.DAYS);
    List<Candidature> expirees =
        candidatureRepository.findByStatutAndDateIngestionBefore(
            StatutCandidature.en_attente, seuil);
    expirees.forEach(Candidature::archiver);
    expirees.forEach(
        c ->
            evenements.publishEvent(
                new CandidatureModifieEvent(c.getId(), "archivage_auto", c.nomComplet())));
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
