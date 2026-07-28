package ma.hbdev.rh.shared.ai;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.Resource;

/**
 * Extraction de texte brut d'un CV (PDFBox/POI, déjà des dépendances du projet) — nécessaire pour
 * {@link OpenRouterCvAnalysisProvider}, dont le modèle gratuit ne comprend que du texte (contraire
 * à Gemini qui lisait le fichier nativement). Formats non couverts : {@code application/msword}
 * (ancien binaire .doc, hors périmètre — nécessiterait poi-scratchpad), images — rejetés
 * explicitement plutôt que silencieusement mal analysés.
 *
 * <p>Bug réel trouvé en conditions réelles (pas deviné) : PDFBox extrait certains PDF (police
 * subset sans CMap ToUnicode composé) en Unicode **décomposé** NFD — "é" ressort en "e" + accent
 * combinant séparé (U+0301), pas en un seul caractère "é". Toujours du Unicode valide, donc aucune
 * perte d'information, mais un tokenizer IA voit une séquence de tokens différente de celle sur
 * laquelle il a été entraîné (texte "normal" = NFC) — repéré sur un vrai CV dont l'IA renvoyait une
 * justification incohérente (mélange de langue) et un mot-clé aberrant. Corrigé par {@link
 * Normalizer#normalize} en NFC avant l'envoi au modèle.
 */
class CvTextExtractor {

  private CvTextExtractor() {}

  static String extraire(Resource cv, String typeMime) throws CvAnalysisException {
    try (InputStream flux = cv.getInputStream()) {
      String texte =
          switch (typeMime) {
            case "application/pdf" -> extrairePdf(flux);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                extraireDocx(flux);
            default ->
                throw new CvAnalysisException(
                    "Format de CV non supporté pour l'analyse IA (extraction de texte) : "
                        + typeMime);
          };
      if (texte == null || texte.isBlank()) {
        throw new CvAnalysisException("Aucun texte extractible de ce CV (page scannée/image ?)");
      }
      return normaliser(texte);
    } catch (IOException e) {
      throw new CvAnalysisException("Échec de lecture du fichier CV", e);
    }
  }

  static String normaliser(String texte) {
    return Normalizer.normalize(texte, Normalizer.Form.NFC);
  }

  private static String extrairePdf(InputStream flux) throws IOException {
    try (PDDocument document = Loader.loadPDF(flux.readAllBytes())) {
      return new PDFTextStripper().getText(document);
    }
  }

  private static String extraireDocx(InputStream flux) throws IOException {
    try (XWPFDocument document = new XWPFDocument(flux);
        XWPFWordExtractor extracteur = new XWPFWordExtractor(document)) {
      return extracteur.getText();
    }
  }
}
