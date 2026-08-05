package ma.hbdev.rh.employee;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.export.TableauExportService;
import ma.hbdev.rh.shared.file.FichierInvalideException;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EmployeService {

  private static final Set<String> TYPES_MIME_PHOTO = Set.of("image/jpeg", "image/png");

  private static final List<String> ENTETES_EXPORT =
      List.of(
          "Nom",
          "Prénom",
          "Email",
          "Téléphone",
          "Poste",
          "Département",
          "Type de contrat",
          "Date d'embauche",
          "Statut",
          "Date de fin de contrat prévue",
          "Date de fin de stage prévue",
          "Date de départ");

  // EF-EXP-01 : le PDF est une fiche de lecture rapide (roster), pas l'export de référence — se
  // limite aux champs utiles à un coup d'œil ; le fichier Excel, lui, garde tous les champs
  // (Email/Téléphone/dates de sortie) pour un usage de données de travail.
  private static final List<String> ENTETES_EXPORT_PDF =
      List.of(
          "Nom", "Prénom", "Département", "Poste", "Type de contrat", "Date d'embauche", "Statut");

  private final EmployeRepository employeRepository;
  private final DepartementRepository departementRepository;
  private final EmployeTransfertRepository transfertRepository;
  private final EmployeDocumentRepository documentRepository;
  private final FileStorageService fileStorageService;
  private final MailService mailService;
  private final ApplicationEventPublisher evenements;
  private final TableauExportService tableauExportService;

  EmployeService(
      EmployeRepository employeRepository,
      DepartementRepository departementRepository,
      EmployeTransfertRepository transfertRepository,
      EmployeDocumentRepository documentRepository,
      FileStorageService fileStorageService,
      MailService mailService,
      ApplicationEventPublisher evenements,
      TableauExportService tableauExportService) {
    this.employeRepository = employeRepository;
    this.departementRepository = departementRepository;
    this.transfertRepository = transfertRepository;
    this.documentRepository = documentRepository;
    this.fileStorageService = fileStorageService;
    this.mailService = mailService;
    this.evenements = evenements;
    this.tableauExportService = tableauExportService;
  }

  @Transactional(readOnly = true)
  Page<Employe> lister(
      UUID departementId,
      UUID managerId,
      TypeContratEmploye typeContrat,
      StatutActifInactif statut,
      String recherche,
      Pageable pageable) {
    UUID departementIdEffectif = departementId;
    if (CurrentUser.hasRole("MANAGER")) {
      UUID departementGere = departementGereParManagerCourant();
      if (departementGere == null
          || (departementId != null && !departementId.equals(departementGere))) {
        return Page.empty(pageable);
      }
      departementIdEffectif = departementGere;
    }
    var specification =
        EmployeSpecifications.filtrer(
            departementIdEffectif, managerId, typeContrat, statut, blancVersNull(recherche));
    return employeRepository.findAll(specification, pageable);
  }

  @Transactional(readOnly = true)
  Employe trouver(UUID id) {
    Employe employe =
        employeRepository
            .findByIdAvecDepartement(id)
            .orElseThrow(() -> new EmployeIntrouvableException(id));
    verifierPerimetreManager(employe);
    return employe;
  }

  @Transactional(readOnly = true)
  public EmployeReponse recuperer(UUID id) {
    return EmployeReponse.depuis(trouver(id));
  }

  // EF-AUTH-03 : le Manager n'a accès en lecture qu'aux employés de son propre département.
  private void verifierPerimetreManager(Employe employe) {
    if (!CurrentUser.hasRole("MANAGER")) {
      return;
    }
    UUID departementGere = departementGereParManagerCourant();
    if (departementGere == null || !departementGere.equals(employe.getDepartement().getId())) {
      throw new AccessDeniedException("Employé hors du périmètre du Manager");
    }
  }

  private UUID departementGereParManagerCourant() {
    UUID managerId =
        CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifié"));
    return departementRepository.findByManagerId(managerId).map(Departement::getId).orElse(null);
  }

  Employe creer(EmployeRequete requete) {
    validerEmailDisponible(requete.email(), null);
    validerDateFinContrat(requete.typeContrat(), requete.dateFinContratPrevue());
    validerDateFinStage(requete.typeContrat(), requete.dateFinStagePrevue());
    Departement departement = trouverDepartement(requete.departementId());
    Employe employe =
        employeRepository.save(
            new Employe(
                requete.nom(),
                requete.prenom(),
                requete.email(),
                requete.telephone(),
                requete.poste(),
                departement,
                requete.managerId(),
                requete.dateEmbauche(),
                requete.typeContrat(),
                requete.dateFinContratPrevue(),
                requete.dateFinStagePrevue(),
                requete.candidatureOrigineId(),
                requete.sexe(),
                requete.cin(),
                requete.sujetStage()));
    evenements.publishEvent(
        EmployeModifieEvent.creation(
            employe.getId(),
            employe.getManagerId() != null ? employe.getManagerId() : departement.getManagerId(),
            employe.getPrenom() + " " + employe.getNom()));
    desactiverSiEcheanceDepassee(employe);
    // EF-EMP-03/EF-EMP-05 : le CV déjà stocké au moment de l'ingestion recrutement est rattaché
    // tel quel comme document employé — pas de reupload, le fichier existe déjà dans shared/file.
    if (requete.cvFichierId() != null) {
      documentRepository.save(
          new EmployeDocument(
              employe.getId(), requete.cvFichierId(), "CV", CurrentUser.id().orElse(null)));
    }
    return employe;
  }

  Employe modifier(UUID id, EmployeModificationRequete requete) {
    Employe employe = trouver(id);
    validerEmailDisponible(requete.email(), employe.getEmail());
    validerDateFinContrat(requete.typeContrat(), requete.dateFinContratPrevue());
    validerDateFinStage(requete.typeContrat(), requete.dateFinStagePrevue());
    employe.modifier(
        requete.nom(),
        requete.prenom(),
        requete.email(),
        requete.telephone(),
        requete.poste(),
        requete.dateEmbauche(),
        requete.typeContrat(),
        requete.dateFinContratPrevue(),
        requete.dateFinStagePrevue(),
        requete.sexe(),
        requete.cin(),
        requete.sujetStage());
    evenements.publishEvent(new EmployeModifieEvent(id, "modification"));
    // EF-EMP-XX : si la nouvelle date de fin saisie est déjà dépassée, désactiver tout de suite
    // plutôt que d'attendre le prochain passage du balayage quotidien (02h30) — sinon un Admin qui
    // corrige rétroactivement une date de fin de contrat verrait l'employé rester "actif" jusqu'au
    // lendemain.
    desactiverSiEcheanceDepassee(employe);
    return employe;
  }

  // EF-EMP-01 : seul champ modifiable par un Manager sur la fiche d'un stagiaire de son
  // département — trouver(id) applique déjà verifierPerimetreManager, donc un Manager hors
  // périmètre reçoit le même AccessDeniedException qu'en lecture.
  Employe modifierSujetStage(UUID id, String sujetStage) {
    Employe employe = trouver(id);
    employe.definirSujetStage(sujetStage);
    evenements.publishEvent(new EmployeModifieEvent(id, "modification"));
    return employe;
  }

  Employe transferer(UUID id, TransfertRequete requete, UUID effectuePar) {
    Employe employe = trouver(id);
    Departement nouveauDepartement = trouverDepartement(requete.nouveauDepartementId());
    UUID ancienDepartementId = employe.getDepartement().getId();
    UUID ancienManagerId = employe.getManagerId();

    employe.transferer(nouveauDepartement, requete.nouveauManagerId());
    transfertRepository.save(
        new EmployeTransfert(
            id,
            ancienDepartementId,
            requete.nouveauDepartementId(),
            ancienManagerId,
            requete.nouveauManagerId(),
            requete.dateEffet(),
            effectuePar));
    evenements.publishEvent(new EmployeModifieEvent(id, "transfert"));
    return employe;
  }

  void desactiver(UUID id, DesactivationRequete requete) {
    Employe employe = trouver(id);
    employe.desactiver(requete.motif(), requete.dateDepart());
    evenements.publishEvent(new EmployeModifieEvent(id, "desactivation"));
  }

  /**
   * Désactivation automatique des CDD/stages dont la date de fin prévue est dépassée (EF-EMP-XX).
   * dateDepart = la date de fin prévue elle-même, pas la date du balayage : l'employé est considéré
   * parti au terme de son contrat, même si le balayage ne tourne qu'une fois par jour.
   *
   * <p>Réutilise {@link #desactiver} (même passage par {@code Employe#desactiver}, même publication
   * de {@link EmployeModifieEvent}) plutôt qu'une mutation directe — c'est ce même événement qui,
   * côté module document, annule la surveillance planifiée encore en attente pour cet employé (cf.
   * SurveillancePlanifieeService#gererEvenementEmploye).
   */
  void desactiverContratsExpires() {
    LocalDate aujourdHui = LocalDate.now();
    for (Employe employe :
        employeRepository.findByStatutAndTypeContratAndDateFinContratPrevueLessThan(
            StatutActifInactif.actif, TypeContratEmploye.CDD, aujourdHui)) {
      employe.desactiver(MotifDepartEmploye.fin_cdd, employe.getDateFinContratPrevue());
      evenements.publishEvent(new EmployeModifieEvent(employe.getId(), "desactivation_auto"));
    }
    for (Employe employe :
        employeRepository.findByStatutAndTypeContratInAndDateFinStagePrevueLessThan(
            StatutActifInactif.actif,
            List.of(TypeContratEmploye.STAGIAIRE, TypeContratEmploye.STAGIAIRE_REMUNERE),
            aujourdHui)) {
      employe.desactiver(MotifDepartEmploye.fin_stage, employe.getDateFinStagePrevue());
      evenements.publishEvent(new EmployeModifieEvent(employe.getId(), "desactivation_auto"));
    }
  }

  /**
   * Pendant unitaire de {@link #desactiverContratsExpires} : appelé juste après une création ou une
   * modification, pour désactiver immédiatement un employé dont la date de fin saisie est déjà dans
   * le passé, plutôt que de le laisser "actif" jusqu'au prochain balayage quotidien.
   */
  private void desactiverSiEcheanceDepassee(Employe employe) {
    if (employe.getStatut() != StatutActifInactif.actif) {
      return;
    }
    LocalDate aujourdHui = LocalDate.now();
    if (employe.getTypeContrat() == TypeContratEmploye.CDD) {
      LocalDate finContrat = employe.getDateFinContratPrevue();
      if (finContrat != null && finContrat.isBefore(aujourdHui)) {
        employe.desactiver(MotifDepartEmploye.fin_cdd, finContrat);
        evenements.publishEvent(new EmployeModifieEvent(employe.getId(), "desactivation_auto"));
      }
    } else if (employe.getTypeContrat() == TypeContratEmploye.STAGIAIRE
        || employe.getTypeContrat() == TypeContratEmploye.STAGIAIRE_REMUNERE) {
      LocalDate finStage = employe.getDateFinStagePrevue();
      if (finStage != null && finStage.isBefore(aujourdHui)) {
        employe.desactiver(MotifDepartEmploye.fin_stage, finStage);
        evenements.publishEvent(new EmployeModifieEvent(employe.getId(), "desactivation_auto"));
      }
    }
  }

  @Transactional(readOnly = true)
  List<EmployeTransfert> historiqueTransferts(UUID employeId) {
    trouver(employeId);
    return transfertRepository.findByEmployeIdOrderByCreeLeDesc(employeId);
  }

  EmployeDocumentReponse attacherDocument(
      UUID employeId, MultipartFile fichier, String typeDocument, UUID televersePar) {
    trouver(employeId);
    var uploade = fileStorageService.televerser(fichier, televersePar);
    EmployeDocument document =
        documentRepository.save(
            new EmployeDocument(employeId, uploade.id(), typeDocument, televersePar));
    evenements.publishEvent(new EmployeModifieEvent(employeId, "document_ajoute"));
    return EmployeDocumentReponse.depuis(document, uploade);
  }

  @Transactional(readOnly = true)
  List<EmployeDocumentReponse> listerDocuments(UUID employeId) {
    trouver(employeId);
    return documentRepository.findByEmployeIdOrderByCreeLeDesc(employeId).stream()
        .map(
            doc ->
                EmployeDocumentReponse.depuis(
                    doc, fileStorageService.recuperer(doc.getFichierId())))
        .toList();
  }

  @Transactional(readOnly = true)
  EmployeDocumentTelecharge telechargerDocument(UUID employeId, UUID documentId) {
    trouver(employeId); // valide l'existence + le périmètre Manager (EF-AUTH-03)
    EmployeDocument document =
        documentRepository
            .findById(documentId)
            .filter(d -> d.getEmployeId().equals(employeId))
            .orElseThrow(() -> new EmployeDocumentIntrouvableException(documentId));
    var metadonnees = fileStorageService.recuperer(document.getFichierId());
    var ressource = fileStorageService.charger(document.getFichierId());
    return new EmployeDocumentTelecharge(
        ressource, metadonnees.nomOriginal(), metadonnees.typeMime());
  }

  Employe televerserPhoto(UUID employeId, MultipartFile fichier, UUID televersePar) {
    Employe employe = trouver(employeId);
    validerPhoto(fichier);
    var uploade = fileStorageService.televerser(fichier, televersePar);
    employe.definirPhoto(uploade.id());
    evenements.publishEvent(new EmployeModifieEvent(employeId, "photo_mise_a_jour"));
    return employe;
  }

  @Transactional(readOnly = true)
  EmployePhotoTelecharge recupererPhoto(UUID employeId) {
    Employe employe = trouver(employeId);
    if (employe.getPhotoFichierId() == null) {
      throw new PhotoEmployeIntrouvableException();
    }
    var metadonnees = fileStorageService.recuperer(employe.getPhotoFichierId());
    var ressource = fileStorageService.charger(employe.getPhotoFichierId());
    return new EmployePhotoTelecharge(ressource, metadonnees.typeMime());
  }

  void supprimerDocument(UUID employeId, UUID documentId) {
    trouver(employeId);
    EmployeDocument document =
        documentRepository
            .findById(documentId)
            .filter(d -> d.getEmployeId().equals(employeId))
            .orElseThrow(() -> new EmployeDocumentIntrouvableException(documentId));
    documentRepository.delete(document);
    evenements.publishEvent(new EmployeModifieEvent(employeId, "document_supprime"));
  }

  EmployeDocumentReponse remplacerDocument(
      UUID employeId, UUID documentId, MultipartFile fichier, UUID televersePar) {
    trouver(employeId);
    EmployeDocument ancien =
        documentRepository
            .findById(documentId)
            .filter(d -> d.getEmployeId().equals(employeId))
            .orElseThrow(() -> new EmployeDocumentIntrouvableException(documentId));
    String typeDocument = ancien.getTypeDocument();
    documentRepository.delete(ancien);
    return attacherDocument(employeId, fichier, typeDocument, televersePar);
  }

  void envoyerCarteParEmail(UUID employeId, CarteEmailRequete requete) {
    Employe employe = trouver(employeId);
    String destinataire =
        requete.destinataire() != null && !requete.destinataire().isBlank()
            ? requete.destinataire()
            : employe.getEmail();
    if (destinataire == null || destinataire.isBlank()) {
      throw new EmployeSansEmailException();
    }
    mailService.sendEmail(destinataire, requete.objet(), requete.corps());
    evenements.publishEvent(new EmployeModifieEvent(employeId, "carte_email_envoyee"));
  }

  private void validerPhoto(MultipartFile fichier) {
    if (fichier == null || fichier.isEmpty()) {
      throw new FichierInvalideException("Photo vide ou absente");
    }
    if (!TYPES_MIME_PHOTO.contains(fichier.getContentType())) {
      throw new FichierInvalideException(
          "La photo doit être au format JPEG ou PNG : " + fichier.getContentType());
    }
  }

  private Departement trouverDepartement(UUID id) {
    return departementRepository
        .findById(id)
        .orElseThrow(() -> new DepartementIntrouvableException(id));
  }

  private void validerEmailDisponible(String emailDemande, String emailActuel) {
    if (emailDemande == null || emailDemande.isBlank()) {
      return;
    }
    boolean emailInchange = emailDemande.equalsIgnoreCase(emailActuel);
    if (!emailInchange && employeRepository.existsByEmailIgnoreCase(emailDemande)) {
      throw new EmployeEmailDejaUtiliseException(emailDemande);
    }
  }

  private void validerDateFinContrat(
      TypeContratEmploye typeContrat, LocalDate dateFinContratPrevue) {
    if (typeContrat != TypeContratEmploye.CDD && dateFinContratPrevue != null) {
      throw new DateFinContratInvalideException();
    }
  }

  private void validerDateFinStage(TypeContratEmploye typeContrat, LocalDate dateFinStagePrevue) {
    boolean estStagiaire =
        typeContrat == TypeContratEmploye.STAGIAIRE
            || typeContrat == TypeContratEmploye.STAGIAIRE_REMUNERE;
    if (!estStagiaire && dateFinStagePrevue != null) {
      throw new DateFinStageInvalideException();
    }
  }

  private static String blancVersNull(String valeur) {
    return (valeur == null || valeur.isBlank()) ? null : valeur;
  }

  // EF-EXP-01 : mêmes filtres/périmètre Manager que lister(), pas de logique dupliquée.
  @Transactional(readOnly = true)
  byte[] exporter(
      FormatExport format,
      UUID departementId,
      UUID managerId,
      TypeContratEmploye typeContrat,
      StatutActifInactif statut,
      String recherche) {
    List<Employe> employes =
        lister(departementId, managerId, typeContrat, statut, recherche, Pageable.unpaged())
            .getContent();
    if (format == FormatExport.pdf) {
      List<List<String>> lignesPdf = employes.stream().map(EmployeService::lignePdf).toList();
      return tableauExportService.generer(format, "Employes", ENTETES_EXPORT_PDF, lignesPdf);
    }
    List<List<String>> lignes = employes.stream().map(EmployeService::ligneExport).toList();
    return tableauExportService.generer(format, "Employes", ENTETES_EXPORT, lignes);
  }

  private static List<String> ligneExport(Employe employe) {
    return List.of(
        texte(employe.getNom()),
        texte(employe.getPrenom()),
        texte(employe.getEmail()),
        texte(employe.getTelephone()),
        texte(employe.getPoste()),
        texte(employe.getDepartement() == null ? null : employe.getDepartement().getNom()),
        texte(employe.getTypeContrat() == null ? null : employe.getTypeContrat().name()),
        texte(employe.getDateEmbauche()),
        texte(employe.getStatut() == null ? null : employe.getStatut().name()),
        texte(employe.getDateFinContratPrevue()),
        texte(employe.getDateFinStagePrevue()),
        texte(employe.getDateDepart()));
  }

  private static List<String> lignePdf(Employe employe) {
    return List.of(
        texte(employe.getNom()),
        texte(employe.getPrenom()),
        texte(employe.getDepartement() == null ? null : employe.getDepartement().getNom()),
        texte(employe.getPoste()),
        texte(employe.getTypeContrat() == null ? null : employe.getTypeContrat().name()),
        texte(employe.getDateEmbauche()),
        texte(employe.getStatut() == null ? null : employe.getStatut().name()));
  }

  private static String texte(Object valeur) {
    return valeur == null ? "" : valeur.toString();
  }
}
