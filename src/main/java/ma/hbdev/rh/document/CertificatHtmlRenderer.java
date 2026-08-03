package ma.hbdev.rh.document;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Rendu HTML/CSS vers PDF pour les certificats stylisés (EF-DOC), via openhtmltopdf.
 *
 * <p>Remplace un empilement de {@code Paragraph} OpenPDF par un template HTML entretenable
 * séparément du code Java (voir {@code src/main/resources/certificats/}). Les polices de marque
 * (Source Serif 4, IBM Plex Sans) sont enregistrées ici depuis des fichiers statiques
 * pré-instanciés (voir {@code src/main/resources/fonts/certificats/}) — openhtmltopdf ne charge
 * jamais de police depuis un CDN, ni de manière fiable une police variable.
 */
@Component
class CertificatHtmlRenderer {

  private static final String DOSSIER_POLICES = "/fonts/certificats/";

  byte[] rendre(String html) {
    try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      enregistrerPolice(builder, "IBMPlexSans-Regular.ttf", "IBM Plex Sans", 400, FontStyle.NORMAL);
      enregistrerPolice(builder, "IBMPlexSans-Bold.ttf", "IBM Plex Sans", 700, FontStyle.NORMAL);
      enregistrerPolice(
          builder, "SourceSerif4-Regular.ttf", "Source Serif 4", 400, FontStyle.NORMAL);
      enregistrerPolice(builder, "SourceSerif4-Bold.ttf", "Source Serif 4", 700, FontStyle.NORMAL);
      builder.withHtmlContent(html, null);
      builder.toStream(sortie);
      builder.run();
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Erreur lors du rendu HTML vers PDF du certificat", e);
    }
  }

  private void enregistrerPolice(
      PdfRendererBuilder builder, String nomFichier, String famille, int graisse, FontStyle style) {
    builder.useFont(() -> ouvrirPolice(nomFichier), famille, graisse, style, true);
  }

  private InputStream ouvrirPolice(String nomFichier) {
    try {
      return new ClassPathResource(DOSSIER_POLICES + nomFichier).getInputStream();
    } catch (IOException e) {
      throw new RuntimeException("Police introuvable : " + nomFichier, e);
    }
  }

  static String chargerTemplate(String nomFichier) {
    try (InputStream flux = new ClassPathResource("/certificats/" + nomFichier).getInputStream()) {
      return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Template de certificat introuvable : " + nomFichier, e);
    }
  }
}
