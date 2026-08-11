package ma.hbdev.rh.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import ma.hbdev.rh.config.IdentiteEntrepriseReponse;
import ma.hbdev.rh.config.IdentiteEntrepriseService;
import ma.hbdev.rh.shared.file.FileStorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class CertificatGeneratorTest {

  private final IdentiteEntrepriseService identiteEntrepriseService =
      mock(IdentiteEntrepriseService.class);
  private final FileStorageService fileStorageService = mock(FileStorageService.class);
  private final CertificatGenerator generator =
      new CertificatGenerator(
          new CertificatHtmlRenderer(), identiteEntrepriseService, fileStorageService);

  private static IdentiteEntrepriseReponse identiteSansSignataire(String raisonSociale) {
    return new IdentiteEntrepriseReponse(
        null,
        raisonSociale,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static IdentiteEntrepriseReponse identiteComplete(
      String raisonSociale,
      String adresse,
      String telephone,
      String email,
      String ice,
      String rc,
      String ville,
      java.util.UUID logoFichierId,
      java.util.UUID signatureFichierId,
      String signataireNom,
      String signataireFonction,
      String signataireSexe) {
    return new IdentiteEntrepriseReponse(
        null,
        raisonSociale,
        adresse,
        telephone,
        email,
        ice,
        rc,
        ville,
        logoFichierId,
        signatureFichierId,
        signataireNom,
        signataireFonction,
        signataireSexe,
        null,
        null);
  }

  @Test
  void testGenererCertificatStage() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                "Tétouan, Maroc",
                "+212 5 00 00 00 00",
                "rh@hbdev.ma",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            "Développement d'une plateforme de gestion RH",
            "HOMME");

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);

    String texte = extraireTexte(pdf);
    assertThat(texte.replaceAll("\\s+", " ")).containsIgnoringCase("attestation de stage");
    assertThat(texte).contains("Jean");
    assertThat(texte).contains("Dupont");
    assertThat(texte).contains("Développeur");
    assertThat(texte).contains("Développement d'une plateforme de gestion RH");
    assertThat(texte).contains("HB Développement");
    // EF-DOC : accord de genre masculin, sans le "il/elle" générique par défaut.
    assertThat(texte).contains("il a fait preuve");
    assertThat(texte).contains("intéressé ");
  }

  @Test
  void testGenererCertificatStage_sexeFemme_accordeAuFeminin() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jeanne",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeuse",
            null,
            "FEMME");

    String texte = extraireTexte(pdf);
    assertThat(texte).contains("elle a fait preuve");
    assertThat(texte).contains("intéressée");
  }

  @Test
  void testGenererCertificatStage_sexeAbsent_degradeVersFormeNeutre() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    String texte = extraireTexte(pdf);
    assertThat(texte).contains("il/elle a fait preuve");
    assertThat(texte).contains("intéressé(e)");
  }

  @Test
  void testGenererCertificatStage_avecSignataireConfigure_utiliseSaFormuleEtSonAccord()
      throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Amal Medah",
                "Responsable RH",
                "FEMME"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    String texte = extraireTexte(pdf);
    assertThat(texte.replaceAll("\\s+", " ")).contains("Je soussignée, Amal Medah, en qualité de");
    assertThat(texte).contains("Responsable RH");
    assertThat(texte).contains("Amal Medah");
  }

  @Test
  void testGenererCertificatStage_sansSignataireConfigure_degradeVersFormuleCollective()
      throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    String texte = extraireTexte(pdf);
    assertThat(texte.replaceAll("\\s+", " ")).contains("Nous soussignés, HB Développement");
  }

  @Test
  void testGenererCertificatStage_sansSujet_nAfficheAucunBlocSujet() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    String texte = extraireTexte(pdf);
    assertThat(texte).doesNotContain("portant sur le sujet");
  }

  @Test
  void testGenererCertificatStage_logoIllisible_negenerePasEnErreur() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.UUID.randomUUID(),
                null,
                null,
                null,
                null));
    when(fileStorageService.recuperer(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new RuntimeException("fichier introuvable"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
  }

  @Test
  void testGenererCertificatStage_avecSignature_incorpoereLImageDansLePdf() throws Exception {
    java.util.UUID signatureFichierId = java.util.UUID.randomUUID();
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                signatureFichierId,
                null,
                null,
                null));
    when(fileStorageService.recuperer(signatureFichierId))
        .thenReturn(
            new ma.hbdev.rh.shared.file.FichierUploade(
                signatureFichierId, "signature.png", "image/png", 3));
    // PNG 1x1 transparent réel (pas des octets arbitraires) : openhtmltopdf valide le format de
    // l'image avant de l'incorporer, un contenu factice serait rejeté comme "illisible" et
    // fausserait ce test-ci (couvert séparément par le test suivant).
    byte[] png1x1 =
        java.util.Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    when(fileStorageService.charger(signatureFichierId))
        .thenReturn(new org.springframework.core.io.ByteArrayResource(png1x1));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
    // Le PDF contient bien une image encodée (preuve indirecte : présence d'un flux XObject dans
    // le document, la seule source d'image de tout le template étant la signature ici puisque le
    // logo est absent).
    try (PDDocument document = Loader.loadPDF(pdf)) {
      boolean auMoinsUneImage =
          document.getPage(0).getResources().getXObjectNames().iterator().hasNext();
      assertThat(auMoinsUneImage).isTrue();
    }
  }

  @Test
  void testGenererCertificatStage_signatureIllisible_negenerePasEnErreur() throws Exception {
    java.util.UUID signatureFichierId = java.util.UUID.randomUUID();
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                signatureFichierId,
                null,
                null,
                null));
    when(fileStorageService.recuperer(signatureFichierId))
        .thenThrow(new RuntimeException("fichier introuvable"));

    byte[] pdf =
        generator.genererCertificatStage(
            "Jean",
            "Dupont",
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 6, 30),
            "Développeur",
            null,
            null);

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
  }

  @Test
  void testGenererCertificatTravail_cdi_homme() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                "Tétouan, Maroc",
                "+212 5 00 00 00 00",
                "rh@hbdev.ma",
                "001234567000089",
                "12345",
                "Tétouan",
                null,
                null,
                "Amal Medah",
                "Responsable RH",
                "FEMME"));

    byte[] pdf =
        generator.genererCertificatTravail(
            "Jean",
            "Dupont",
            "AB123456",
            "Manager",
            "CDI",
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2023, 12, 31),
            "HOMME");

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);

    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).containsIgnoringCase("certificat de travail");
    assertThat(texte).contains("Monsieur Jean Dupont");
    assertThat(texte).contains("AB123456");
    assertThat(texte).contains("Manager");
    assertThat(texte).contains("durée indéterminée");
    assertThat(texte).contains("a été employé au sein");
    assertThat(texte).contains("Je soussignée, Amal Medah, en qualité de");
    assertThat(texte).contains("certifie que");
    assertThat(texte).contains("ICE : 001234567000089");
    assertThat(texte).contains("RC : 12345");
    assertThat(texte).contains("Fait à Tétouan");
  }

  @Test
  void testGenererCertificatTravail_cdd_femme() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatTravail(
            "Jeanne",
            "Dupont",
            null,
            "Développeuse",
            "CDD",
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2023, 12, 31),
            "FEMME");

    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).contains("durée déterminée");
    assertThat(texte).contains("Madame Jeanne Dupont");
    assertThat(texte).contains("a été employée au sein");
    assertThat(texte).contains("Nous soussignés, HB Développement");
    assertThat(texte).contains("certifions que");
  }

  @Test
  void testGenererCertificatTravail_sexeEtCinAbsents_degradeSansErreur() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererCertificatTravail(
            "Jean",
            "Dupont",
            null,
            "Développeur",
            "CDI",
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2023, 12, 31),
            null);

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).contains("a été employé(e) au sein");
  }

  @Test
  void testGenererAttestationTravail_cdi_homme() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(
            identiteComplete(
                "HB Développement",
                "Tétouan, Maroc",
                "+212 5 00 00 00 00",
                "rh@hbdev.ma",
                "001234567000089",
                "12345",
                "Tétouan",
                null,
                null,
                "Amal Medah",
                "Responsable RH",
                "FEMME"));

    byte[] pdf =
        generator.genererAttestationTravail(
            "Jean", "Dupont", "AB123456", "Développeur", "CDI", LocalDate.of(2023, 1, 1), "HOMME");

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);

    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).containsIgnoringCase("attestation de travail");
    assertThat(texte).contains("Jean");
    assertThat(texte).contains("Dupont");
    assertThat(texte).contains("AB123456");
    assertThat(texte).contains("Développeur");
    assertThat(texte).contains("durée indéterminée");
    assertThat(texte).contains("Je soussignée, Amal Medah, en qualité de");
    assertThat(texte).contains("ICE : 001234567000089");
    assertThat(texte).contains("RC : 12345");
    assertThat(texte).contains("Fait à Tétouan");
    // Accord au masculin pour l'employé (sexe HOMME), quel que soit le sexe du signataire.
    assertThat(texte).contains("Monsieur Jean Dupont");
    assertThat(texte).contains("est employé au sein");
    assertThat(texte).contains("il occupe toujours ce poste");
  }

  @Test
  void testGenererAttestationTravail_cdd_femme() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererAttestationTravail(
            "Jeanne", "Dupont", null, "Développeuse", "CDD", LocalDate.of(2023, 1, 1), "FEMME");

    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).contains("durée déterminée");
    assertThat(texte).contains("Madame Jeanne Dupont");
    assertThat(texte).contains("est employée au sein");
    assertThat(texte).contains("elle occupe toujours ce poste");
  }

  @Test
  void testGenererAttestationTravail_sexeEtCinAbsents_degradeSansErreur() throws Exception {
    when(identiteEntrepriseService.obtenir())
        .thenReturn(identiteSansSignataire("HB Développement"));

    byte[] pdf =
        generator.genererAttestationTravail(
            "Jean", "Dupont", null, "Développeur", "CDI", LocalDate.of(2023, 1, 1), null);

    assertNotNull(pdf);
    assertTrue(pdf.length > 0);
    String texte = extraireTexte(pdf).replaceAll("\\s+", " ");
    assertThat(texte).contains("est employé(e) au sein");
    assertThat(texte).contains("il/elle occupe toujours ce poste");
  }

  private String extraireTexte(byte[] pdf) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      // setSortByPosition : sans ça, PDFTextStripper restitue le texte dans l'ordre d'écriture du
      // flux de contenu PDF, qu'openhtmltopdf regroupe par police (donc les passages <strong>,
      // dans une police différente, se retrouvent extraits à part du texte normal environnant) —
      // pas un défaut d'affichage visuel (chaque passage garde ses coordonnées correctes sur la
      // page), seulement un piège d'extraction si on ne trie pas par position réelle.
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(document);
    }
  }
}
