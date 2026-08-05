package ma.hbdev.rh.document;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.employee.EmployeReponse;
import ma.hbdev.rh.employee.EmployeService;
import ma.hbdev.rh.shared.file.FichierUploade;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.mail.RestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
class DocumentRhService {

  private final EnvoiDocumentRhRepository envoiDocumentRepository;
  private final EmployeService employeService;
  private final CertificatGenerator certificatGenerator;
  private final FileStorageService fileStorageService;
  private final RestClient restClient;
  private final String webhookUrl;
  private final String apiBaseUrl;
  private final NotificationPlanifieeRepository notificationPlanifieeRepository;

  DocumentRhService(
      EnvoiDocumentRhRepository envoiDocumentRepository,
      EmployeService employeService,
      CertificatGenerator certificatGenerator,
      FileStorageService fileStorageService,
      RestClient.Builder restClientBuilder,
      @Value("${app.n8n.base-url:http://localhost:5678}") String n8nBaseUrl,
      @Value("${app.api-base-url:http://localhost:8080}") String apiBaseUrl,
      NotificationPlanifieeRepository notificationPlanifieeRepository) {
    this.envoiDocumentRepository = envoiDocumentRepository;
    this.employeService = employeService;
    this.certificatGenerator = certificatGenerator;
    this.fileStorageService = fileStorageService;
    this.restClient =
        RestClientFactory.buildWithTimeouts(
            restClientBuilder, Duration.ofSeconds(5), Duration.ofSeconds(10));
    this.webhookUrl = MailService.urlWebhookNotifyEmail(n8nBaseUrl);
    this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
    this.notificationPlanifieeRepository = notificationPlanifieeRepository;
  }

  // EF-AUTH-03 : recuperer() applique le périmètre Manager (propre département) avant d'exposer
  // l'historique — sans cet appel, un Manager pourrait lister l'historique de n'importe quel
  // employeId en le devinant, malgré la restriction de lecture voulue.
  @Transactional(readOnly = true)
  List<EnvoiDocumentResponse> listerEnvois(UUID employeId) {
    employeService.recuperer(employeId);
    return envoiDocumentRepository.findByEmployeIdOrderByDateEnvoiDesc(employeId).stream()
        .map(EnvoiDocumentResponse::depuis)
        .toList();
  }

  EnvoiDocument renvoyerDepuisSurveillance(UUID notifId, UUID envoyePar) {
    NotificationPlanifiee notif =
        notificationPlanifieeRepository
            .findById(notifId)
            .orElseThrow(() -> new IllegalArgumentException("Notification introuvable"));

    UUID employeId = notif.getEmployeId();
    EnvoiDocument envoi;
    if (notif.getTypeSurveillance() == TypeFinSurveillee.fin_stage) {
      envoi = envoyerCertificatStage(employeId, null, envoyePar);
    } else {
      envoi = envoyerCertificatTravail(employeId, envoyePar);
    }

    if (notif.getStatut() == StatutNotificationPlanifiee.planifiee) {
      notif.marquerEnvoyee();
    } else if (notif.getStatut() == StatutNotificationPlanifiee.envoyee) {
      notif.marquerRelancee();
    }
    notificationPlanifieeRepository.save(notif);

    return envoi;
  }

  /**
   * EF-DOC : aperçu du PDF avant envoi — même génération que l'envoi, sans e-mail ni persistance.
   */
  byte[] apercuCertificatStage(UUID employeId, String sujetStage) {
    return genererPdfCertificatStage(trouverEmploye(employeId), sujetStage);
  }

  byte[] apercuCertificatTravail(UUID employeId) {
    return genererPdfCertificatTravail(trouverEmploye(employeId));
  }

  byte[] apercuAttestationTravail(UUID employeId) {
    EmployeReponse employe = trouverEmploye(employeId);
    validerEligibiliteAttestation(employe);
    return genererPdfAttestationTravail(employe);
  }

  private byte[] genererPdfCertificatStage(EmployeReponse employe, String sujetStage) {
    return certificatGenerator.genererCertificatStage(
        employe.prenom(),
        employe.nom(),
        employe.dateEmbauche(),
        employe.dateFinStagePrevue(),
        employe.poste(),
        sujetStage,
        employe.sexe());
  }

  private byte[] genererPdfCertificatTravail(EmployeReponse employe) {
    return certificatGenerator.genererCertificatTravail(
        employe.prenom(),
        employe.nom(),
        employe.cin(),
        employe.poste(),
        employe.typeContrat(),
        employe.dateEmbauche(),
        employe.dateDepart(),
        employe.sexe());
  }

  private byte[] genererPdfAttestationTravail(EmployeReponse employe) {
    return certificatGenerator.genererAttestationTravail(
        employe.prenom(),
        employe.nom(),
        employe.cin(),
        employe.poste(),
        employe.typeContrat(),
        employe.dateEmbauche(),
        employe.sexe());
  }

  private void validerEligibiliteAttestation(EmployeReponse employe) {
    if (!"actif".equals(employe.statut())) {
      throw new IllegalArgumentException(
          "L'attestation de travail n'est disponible que pour un employé actif.");
    }
    if (!"CDI".equals(employe.typeContrat()) && !"CDD".equals(employe.typeContrat())) {
      throw new IllegalArgumentException(
          "L'attestation de travail n'est disponible que pour un employé CDI ou CDD.");
    }
  }

  EnvoiDocument envoyerCertificatStage(UUID employeId, String sujetStage, UUID envoyePar) {
    EmployeReponse employe = trouverEmploye(employeId);
    byte[] pdf = genererPdfCertificatStage(employe, sujetStage);

    return traiterEnvoi(
        employe,
        pdf,
        "certificat_stage.pdf",
        TypeDocumentRh.certificat_stage,
        "Votre certificat de stage",
        "Bonjour,\n\nVeuillez trouver ci-joint votre certificat de stage. "
            + "Merci de passer au bureau pour récupérer l'original.\n\nCordialement, RH",
        envoyePar);
  }

  EnvoiDocument envoyerCertificatTravail(UUID employeId, UUID envoyePar) {
    EmployeReponse employe = trouverEmploye(employeId);
    byte[] pdf = genererPdfCertificatTravail(employe);

    return traiterEnvoi(
        employe,
        pdf,
        "certificat_travail.pdf",
        TypeDocumentRh.certificat_travail,
        "Votre certificat de travail",
        "Bonjour,\n\nVeuillez trouver ci-joint votre certificat de travail suite à votre départ.\n\nCordialement, RH",
        envoyePar);
  }

  /**
   * Attestation de travail — employé encore ACTIF, distincte de {@link #envoyerCertificatTravail}
   * qui documente un départ. Deux validations propres à ce document (pas de contrainte CHECK
   * équivalente en base, la table {@code envois_documents} reste générique pour tout {@link
   * TypeDocumentRh}) :
   */
  EnvoiDocument envoyerAttestationTravail(UUID employeId, UUID envoyePar) {
    EmployeReponse employe = trouverEmploye(employeId);
    validerEligibiliteAttestation(employe);
    byte[] pdf = genererPdfAttestationTravail(employe);

    return traiterEnvoi(
        employe,
        pdf,
        "attestation_travail.pdf",
        TypeDocumentRh.attestation_travail,
        "Votre attestation de travail",
        "Bonjour,\n\nVeuillez trouver ci-joint votre attestation de travail.\n\nCordialement, RH",
        envoyePar);
  }

  EnvoiDocument envoyerDocumentLibre(UUID employeId, MultipartFile file, UUID envoyePar) {
    EmployeReponse employe = trouverEmploye(employeId);
    try {
      return traiterEnvoi(
          employe,
          file.getBytes(),
          file.getOriginalFilename(),
          TypeDocumentRh.document_libre,
          "Nouveau document RH",
          "Bonjour,\n\nVeuillez trouver un document RH en pièce jointe.\n\nCordialement, RH",
          envoyePar);
    } catch (Exception e) {
      throw new RuntimeException("Erreur lors de la lecture du fichier", e);
    }
  }

  private EnvoiDocument traiterEnvoi(
      EmployeReponse employe,
      byte[] contenuFichier,
      String nomFichier,
      TypeDocumentRh typeDocument,
      String sujet,
      String corps,
      UUID envoyePar) {

    // Sauvegarder le fichier généré
    MultipartFile mockFile =
        new ByteArrayMultipartFile(contenuFichier, "file", nomFichier, "application/pdf");
    FichierUploade fichier = fileStorageService.televerser(mockFile, envoyePar);

    String destinataire = employe.email();
    if (destinataire == null || destinataire.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "L'adresse e-mail de l'employé est requise pour envoyer ce document.");
    }

    // Lien de téléchargement. L'URL publique de l'API vient de la configuration
    // (app.api-base-url) : en dur, le lien serait inutilisable dès que le destinataire
    // n'est pas sur le poste du serveur.
    String lienTelechargement = apiBaseUrl + "/api/fichiers/" + fichier.id();
    String messageFinal = corps + "\n\nTélécharger le document : " + lienTelechargement;

    // Envoi via n8n
    envoyerEmailViaWebhook(destinataire, sujet, messageFinal);

    EnvoiDocument envoi =
        new EnvoiDocument(
            employe.id(), typeDocument, fichier.id(), destinataire, messageFinal, envoyePar);

    return envoiDocumentRepository.save(envoi);
  }

  private void envoyerEmailViaWebhook(String to, String subject, String message) {
    record N8nPayload(String to, String subject, String message) {}
    try {
      restClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new N8nPayload(to, subject, message))
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      throw new EnvoiWebhookEchoueException(
          "Échec de l'envoi du document via le webhook n8n : " + e.getMessage(), e);
    }
  }

  private EmployeReponse trouverEmploye(UUID id) {
    return employeService.recuperer(id);
  }
}
