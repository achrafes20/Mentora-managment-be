package ma.hbdev.rh.document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import ma.hbdev.rh.config.IdentiteEntrepriseReponse;
import ma.hbdev.rh.config.IdentiteEntrepriseService;
import ma.hbdev.rh.shared.file.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
class CertificatGenerator {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CertificatHtmlRenderer htmlRenderer;
  private final IdentiteEntrepriseService identiteEntrepriseService;
  private final FileStorageService fileStorageService;

  CertificatGenerator(
      CertificatHtmlRenderer htmlRenderer,
      IdentiteEntrepriseService identiteEntrepriseService,
      FileStorageService fileStorageService) {
    this.htmlRenderer = htmlRenderer;
    this.identiteEntrepriseService = identiteEntrepriseService;
    this.fileStorageService = fileStorageService;
  }

  /**
   * Certificat de stage — rendu HTML/CSS stylisé (openhtmltopdf), seul document à afficher le sujet
   * de stage.
   */
  byte[] genererCertificatStage(
      String prenom,
      String nom,
      LocalDate dateEmbauche,
      LocalDate dateFin,
      String poste,
      String sujetStage,
      String sexeEmploye) {
    IdentiteEntrepriseReponse identite = identiteEntrepriseService.obtenir();

    String introSujet = " ";
    String blocSujetStage = "";
    if (sujetStage != null && !sujetStage.isBlank()) {
      introSujet = ", portant sur le sujet suivant :";
      // Caractères Unicode littéraux (« » espace insécable), pas d'entités nommées HTML
      // (&laquo;/&nbsp;) : openhtmltopdf parse le HTML comme du XML strict, qui ne connaît que
      // les 5 entités XML de base (&amp; &lt; &gt; &apos; &quot;) — cf. CertificatHtmlRenderer.
      blocSujetStage =
          "<div class=\"sujet-stage-bloc\"><div class=\"sujet-stage\">« "
              + echapper(sujetStage)
              + " »</div></div>";
    } else {
      introSujet = ".";
    }

    String html =
        CertificatHtmlRenderer.chargerTemplate("certificat-stage.html")
            .replace(
                "{{raisonSociale}}", valeurOuVide(identite.raisonSociale(), "HB Développement"))
            .replace("{{adresse}}", valeurOuVide(identite.adresse(), ""))
            .replace("{{telephone}}", valeurOuVide(identite.telephone(), ""))
            .replace("{{email}}", valeurOuVide(identite.email(), ""))
            .replace("{{logoImgTag}}", fichierImgTag(identite.logoFichierId(), "Logo", null))
            .replace("{{prenom}}", echapper(prenom))
            .replace("{{nom}}", echapper(nom))
            .replace("{{poste}}", echapper(valeurOuVide(poste, "")))
            .replace("{{dateEmbauche}}", format(dateEmbauche))
            .replace("{{dateFin}}", format(dateFin))
            .replace("{{introSujet}}", introSujet)
            .replace("{{blocSujetStage}}", blocSujetStage)
            .replace("{{blocSignature}}", blocSignature(identite))
            .replace(
                "{{introSignataire}}",
                introSignataire(identite, "attestons par la présente", "atteste par la présente"))
            .replace(
                "{{signataireFonctionAffichee}}",
                echapper(
                    valeurOuVide(
                        identite.signataireFonction(), "La Direction des Ressources Humaines")))
            .replace("{{pronomEmploye}}", pronom(sexeEmploye))
            .replace("{{interesseEmploye}}", interesse(sexeEmploye))
            .replace("{{dateGeneration}}", format(LocalDate.now()));

    return htmlRenderer.rendre(html);
  }

  /**
   * Attestation de travail — employé encore ACTIF ("occupe toujours ce poste"), distincte du
   * certificat de travail ci-dessous qui documente un départ (EF-DOC-10, avec date de fin).
   * Réservée aux CDI/CDD (DocumentRhService valide le type de contrat et le statut actif avant
   * d'appeler cette méthode) — la notion de "durée déterminée/indéterminée" n'a pas de sens pour un
   * stagiaire.
   */
  byte[] genererAttestationTravail(
      String prenom,
      String nom,
      String cin,
      String poste,
      String typeContrat,
      LocalDate dateEmbauche,
      String sexeEmploye) {
    IdentiteEntrepriseReponse identite = identiteEntrepriseService.obtenir();

    String blocCin =
        (cin == null || cin.isBlank()) ? "" : ", titulaire de la CIN n° " + echapper(cin);

    String html =
        CertificatHtmlRenderer.chargerTemplate("attestation-travail.html")
            .replace(
                "{{raisonSociale}}", valeurOuVide(identite.raisonSociale(), "HB Développement"))
            .replace("{{adresse}}", valeurOuVide(identite.adresse(), ""))
            .replace("{{ligneIdentifiantsLegaux}}", ligneIdentifiantsLegaux(identite))
            .replace("{{logoImgTag}}", fichierImgTag(identite.logoFichierId(), "Logo", null))
            .replace(
                "{{introSignataire}}",
                introSignataire(identite, "attestons par la présente", "atteste par la présente"))
            .replace("{{civiliteEmploye}}", civilite(sexeEmploye))
            .replace("{{prenom}}", echapper(prenom))
            .replace("{{nom}}", echapper(nom))
            .replace("{{blocCin}}", blocCin)
            .replace("{{estEmploye}}", estEmploye(sexeEmploye))
            .replace("{{poste}}", echapper(valeurOuVide(poste, "")))
            .replace("{{dureeContrat}}", dureeContrat(typeContrat))
            .replace("{{dateEmbauche}}", format(dateEmbauche))
            .replace("{{pronomEmploye}}", pronom(sexeEmploye))
            .replace("{{interesseEmploye}}", interesse(sexeEmploye))
            .replace("{{ville}}", echapper(valeurOuVide(identite.ville(), "Tétouan")))
            .replace("{{dateGeneration}}", format(LocalDate.now()))
            .replace("{{blocSignature}}", blocSignature(identite))
            .replace(
                "{{signataireNomAffiche}}", echapper(valeurOuVide(identite.signataireNom(), "")))
            .replace(
                "{{signataireFonctionAffichee}}",
                echapper(
                    valeurOuVide(
                        identite.signataireFonction(), "La Direction des Ressources Humaines")));

    return htmlRenderer.rendre(html);
  }

  /**
   * Attestation de salaire — même éligibilité que l'attestation de travail (actif, CDI/CDD),
   * gabarit identique avec un paragraphe salaire à la place de "occupe toujours ce poste".
   */
  byte[] genererAttestationSalaire(
      String prenom,
      String nom,
      String cin,
      String poste,
      String typeContrat,
      LocalDate dateEmbauche,
      String sexeEmploye,
      java.math.BigDecimal salaireBrutMensuel) {
    IdentiteEntrepriseReponse identite = identiteEntrepriseService.obtenir();

    String blocCin =
        (cin == null || cin.isBlank()) ? "" : ", titulaire de la CIN n° " + echapper(cin);

    String html =
        CertificatHtmlRenderer.chargerTemplate("attestation-salaire.html")
            .replace(
                "{{raisonSociale}}", valeurOuVide(identite.raisonSociale(), "HB Développement"))
            .replace("{{adresse}}", valeurOuVide(identite.adresse(), ""))
            .replace("{{ligneIdentifiantsLegaux}}", ligneIdentifiantsLegaux(identite))
            .replace("{{logoImgTag}}", fichierImgTag(identite.logoFichierId(), "Logo", null))
            .replace(
                "{{introSignataire}}",
                introSignataire(identite, "attestons par la présente", "atteste par la présente"))
            .replace("{{civiliteEmploye}}", civilite(sexeEmploye))
            .replace("{{prenom}}", echapper(prenom))
            .replace("{{nom}}", echapper(nom))
            .replace("{{blocCin}}", blocCin)
            .replace("{{estEmploye}}", estEmploye(sexeEmploye))
            .replace("{{poste}}", echapper(valeurOuVide(poste, "")))
            .replace("{{dureeContrat}}", dureeContrat(typeContrat))
            .replace("{{dateEmbauche}}", format(dateEmbauche))
            .replace("{{pronomEmploye}}", pronom(sexeEmploye))
            .replace("{{salaireBrutMensuel}}", formatMontant(salaireBrutMensuel))
            .replace("{{interesseEmploye}}", interesse(sexeEmploye))
            .replace("{{ville}}", echapper(valeurOuVide(identite.ville(), "Tétouan")))
            .replace("{{dateGeneration}}", format(LocalDate.now()))
            .replace("{{blocSignature}}", blocSignature(identite))
            .replace(
                "{{signataireNomAffiche}}", echapper(valeurOuVide(identite.signataireNom(), "")))
            .replace(
                "{{signataireFonctionAffichee}}",
                echapper(
                    valeurOuVide(
                        identite.signataireFonction(), "La Direction des Ressources Humaines")));

    return htmlRenderer.rendre(html);
  }

  private String formatMontant(java.math.BigDecimal montant) {
    if (montant == null) {
      return "___________";
    }
    return montant.setScale(2, java.math.RoundingMode.HALF_UP) + " MAD";
  }

  private String ligneIdentifiantsLegaux(IdentiteEntrepriseReponse identite) {
    java.util.List<String> parties = new java.util.ArrayList<>();
    if (identite.ice() != null && !identite.ice().isBlank()) {
      parties.add("ICE : " + echapper(identite.ice()));
    }
    if (identite.rc() != null && !identite.rc().isBlank()) {
      parties.add("RC : " + echapper(identite.rc()));
    }
    if (identite.telephone() != null && !identite.telephone().isBlank()) {
      parties.add("Tél : " + echapper(identite.telephone()));
    }
    return String.join(" | ", parties);
  }

  private String civilite(String sexe) {
    if ("HOMME".equals(sexe)) {
      return "Monsieur";
    }
    if ("FEMME".equals(sexe)) {
      return "Madame";
    }
    return "Monsieur/Madame";
  }

  private String estEmploye(String sexe) {
    if ("HOMME".equals(sexe)) {
      return "est employé";
    }
    if ("FEMME".equals(sexe)) {
      return "est employée";
    }
    return "est employé(e)";
  }

  private String dureeContrat(String typeContrat) {
    if ("CDD".equals(typeContrat)) {
      return "déterminée";
    }
    return "indéterminée";
  }

  private String etatEmploye(String sexe) {
    if ("HOMME".equals(sexe)) {
      return "a été employé";
    }
    if ("FEMME".equals(sexe)) {
      return "a été employée";
    }
    return "a été employé(e)";
  }

  /**
   * Certificat de travail — départ d'un employé (EF-DOC-10, avec date de fin), à distinguer de
   * l'attestation de travail ci-dessus qui documente un employé encore actif.
   */
  byte[] genererCertificatTravail(
      String prenom,
      String nom,
      String cin,
      String poste,
      String typeContrat,
      LocalDate dateEmbauche,
      LocalDate dateDepart,
      String sexeEmploye) {
    IdentiteEntrepriseReponse identite = identiteEntrepriseService.obtenir();

    String blocCin =
        (cin == null || cin.isBlank()) ? "" : ", titulaire de la CIN n° " + echapper(cin);

    String html =
        CertificatHtmlRenderer.chargerTemplate("certificat-travail.html")
            .replace(
                "{{raisonSociale}}", valeurOuVide(identite.raisonSociale(), "HB Développement"))
            .replace("{{adresse}}", valeurOuVide(identite.adresse(), ""))
            .replace("{{ligneIdentifiantsLegaux}}", ligneIdentifiantsLegaux(identite))
            .replace("{{logoImgTag}}", fichierImgTag(identite.logoFichierId(), "Logo", null))
            .replace("{{introSignataire}}", introSignataire(identite, "certifions", "certifie"))
            .replace("{{civiliteEmploye}}", civilite(sexeEmploye))
            .replace("{{prenom}}", echapper(prenom))
            .replace("{{nom}}", echapper(nom))
            .replace("{{blocCin}}", blocCin)
            .replace("{{etatEmploye}}", etatEmploye(sexeEmploye))
            .replace("{{poste}}", echapper(valeurOuVide(poste, "")))
            .replace("{{dureeContrat}}", dureeContrat(typeContrat))
            .replace("{{dateEmbauche}}", format(dateEmbauche))
            .replace("{{dateDepart}}", format(dateDepart))
            .replace("{{interesseEmploye}}", interesse(sexeEmploye))
            .replace("{{ville}}", echapper(valeurOuVide(identite.ville(), "Tétouan")))
            .replace("{{dateGeneration}}", format(LocalDate.now()))
            .replace("{{blocSignature}}", blocSignature(identite))
            .replace(
                "{{signataireNomAffiche}}", echapper(valeurOuVide(identite.signataireNom(), "")))
            .replace(
                "{{signataireFonctionAffichee}}",
                echapper(
                    valeurOuVide(
                        identite.signataireFonction(), "La Direction des Ressources Humaines")));

    return htmlRenderer.rendre(html);
  }

  /**
   * Bloc signature/cachet : si l'Admin RH en a téléversé une image (Configuration → identité de
   * l'entreprise), elle remplace l'encart vide à compléter à la main. Dégradation gracieuse dans
   * les deux sens (pas de signature, ou image illisible) vers l'ancien encart en pointillés.
   */
  private String blocSignature(IdentiteEntrepriseReponse identite) {
    String img = fichierImgTag(identite.signatureFichierId(), "Signature et cachet", null);
    if (img.isEmpty()) {
      return "<div class=\"espace-signature\">Signature et cachet</div>";
    }
    return "<div class=\"image-signature\">" + img + "</div>";
  }

  /**
   * Paragraphe d'ouverture, au nom du signataire RH configuré (Configuration → identité de
   * l'entreprise) s'il existe, sinon dégradation vers la formule collective d'origine (pas de nom
   * de personne physique à citer si aucun signataire n'a été renseigné). Le verbe varie selon le
   * document ("atteste"/"attestons" pour le certificat de stage et l'attestation de travail,
   * "certifie"/"certifions" pour le certificat de travail).
   */
  private String introSignataire(
      IdentiteEntrepriseReponse identite, String verbePluriel, String verbeSingulier) {
    String raisonSociale = valeurOuVide(identite.raisonSociale(), "HB Développement");
    if (identite.signataireNom() == null || identite.signataireNom().isBlank()) {
      return "Nous soussignés, <strong>"
          + echapper(raisonSociale)
          + "</strong>, "
          + verbePluriel
          + " que :";
    }
    String soussigne = soussigne(identite.signataireSexe());
    String fonction = valeurOuVide(identite.signataireFonction(), "Responsable RH");
    return "Je "
        + soussigne
        + ", <strong>"
        + echapper(identite.signataireNom())
        + "</strong>, en qualité de "
        + echapper(fonction)
        + " de la société <strong>"
        + echapper(raisonSociale)
        + "</strong>, "
        + verbeSingulier
        + " que :";
  }

  private String soussigne(String sexeSignataire) {
    if ("HOMME".equals(sexeSignataire)) {
      return "soussigné";
    }
    if ("FEMME".equals(sexeSignataire)) {
      return "soussignée";
    }
    return "soussigné(e)";
  }

  private String pronom(String sexe) {
    if ("HOMME".equals(sexe)) {
      return "il";
    }
    if ("FEMME".equals(sexe)) {
      return "elle";
    }
    return "il/elle";
  }

  private String interesse(String sexe) {
    if ("HOMME".equals(sexe)) {
      return "intéressé";
    }
    if ("FEMME".equals(sexe)) {
      return "intéressée";
    }
    return "intéressé(e)";
  }

  private String fichierImgTag(java.util.UUID fichierId, String texteAlt, String classeCss) {
    if (fichierId == null) {
      return "";
    }
    try {
      var metadonnees = fileStorageService.recuperer(fichierId);
      var ressource = fileStorageService.charger(fichierId);
      byte[] octets = StreamUtils.copyToByteArray(ressource.getInputStream());
      String base64 = Base64.getEncoder().encodeToString(octets);
      String classeAttribut = classeCss == null ? "" : " class=\"" + classeCss + "\"";
      return "<img"
          + classeAttribut
          + " src=\"data:"
          + metadonnees.typeMime()
          + ";base64,"
          + base64
          + "\" alt=\""
          + echapper(texteAlt)
          + "\" />";
    } catch (Exception e) {
      // Dégradation gracieuse : un fichier illisible ne doit jamais empêcher la génération du
      // certificat, seulement l'omettre visuellement.
      return "";
    }
  }

  private String valeurOuVide(String valeur, String defaut) {
    return (valeur == null || valeur.isBlank()) ? defaut : valeur;
  }

  private String echapper(String valeur) {
    if (valeur == null) {
      return "";
    }
    return valeur
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private String format(LocalDate date) {
    if (date == null) {
      return "___/___/_____";
    }
    return date.format(DATE_FORMATTER);
  }
}
