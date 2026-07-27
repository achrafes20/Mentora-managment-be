package ma.hbdev.rh.document;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
class CertificatGenerator {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  byte[] genererCertificatStage(String prenom, String nom, LocalDate dateEmbauche, LocalDate dateFin, String poste) {
    return genererDocument(
        "CERTIFICAT DE STAGE",
        "Nous soussignés, HB Développement, attestons par la présente que :",
        prenom + " " + nom,
        "a effectué un stage au sein de notre entreprise en qualité de :",
        poste,
        "du " + format(dateEmbauche) + " au " + format(dateFin) + ".");
  }

  byte[] genererCertificatTravail(String prenom, String nom, LocalDate dateEmbauche, LocalDate dateDepart, String poste) {
    return genererDocument(
        "CERTIFICAT DE TRAVAIL",
        "Nous soussignés, HB Développement, attestons par la présente que :",
        prenom + " " + nom,
        "a été employé(e) au sein de notre entreprise en qualité de :",
        poste,
        "du " + format(dateEmbauche) + " au " + format(dateDepart) + ".");
  }

  private byte[] genererDocument(
      String titre, String intro, String nomComplet, String texteMilieu, String poste, String texteFin) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document();
      PdfWriter.getInstance(document, baos);
      document.open();

      Font fontTitre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
      Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 12);
      Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);

      // En-tête (Entreprise)
      Paragraph header = new Paragraph("HB Développement\nService des Ressources Humaines\n\n", fontBold);
      header.setAlignment(Element.ALIGN_LEFT);
      document.add(header);

      // Date de génération
      Paragraph dateGen = new Paragraph("Fait le : " + format(LocalDate.now()) + "\n\n", fontNormal);
      dateGen.setAlignment(Element.ALIGN_RIGHT);
      document.add(dateGen);

      // Titre
      Paragraph pTitre = new Paragraph(titre + "\n\n\n", fontTitre);
      pTitre.setAlignment(Element.ALIGN_CENTER);
      document.add(pTitre);

      // Corps du texte
      document.add(new Paragraph(intro + "\n\n", fontNormal));
      
      Paragraph pNom = new Paragraph(nomComplet + "\n\n", fontBold);
      pNom.setAlignment(Element.ALIGN_CENTER);
      document.add(pNom);

      document.add(new Paragraph(texteMilieu + "\n\n", fontNormal));

      Paragraph pPoste = new Paragraph(poste + "\n\n", fontBold);
      pPoste.setAlignment(Element.ALIGN_CENTER);
      document.add(pPoste);

      document.add(new Paragraph(texteFin + "\n\n\n", fontNormal));

      // Signature
      Paragraph signature = new Paragraph("La Direction des Ressources Humaines", fontNormal);
      signature.setAlignment(Element.ALIGN_RIGHT);
      document.add(signature);

      document.close();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Erreur lors de la génération du document PDF", e);
    }
  }

  private String format(LocalDate date) {
    if (date == null) {
      return "___/___/_____";
    }
    return date.format(DATE_FORMATTER);
  }
}
