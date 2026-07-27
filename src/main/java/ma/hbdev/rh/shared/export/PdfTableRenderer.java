package ma.hbdev.rh.shared.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Rendu PDF générique à partir d'en-têtes/lignes déjà formatées en texte. Colonnes de largeur
 * proportionnelle au contenu (même principe que {@link ExcelTableRenderer}, plutôt qu'une largeur
 * égale qui écrase les colonnes courtes — ex. "Statut" — et tronque les longues — ex. "Motif").
 * Pagination automatique en paysage A4 avec ré-affichage de l'en-tête à chaque page — pas de mise
 * en page avancée (retour à la ligne, fusion de cellules), volontairement hors périmètre EF-EXP-04
 * (export à la demande, pas un moteur de reporting).
 *
 * <p>Police Helvetica standard 14 (aucun fichier de police à embarquer), donc encodage limité à
 * l'ASCII imprimable : tout le texte est passé par {@link #versAsciiSimple} (accents décomposés
 * puis retirés, ex. "Généré" → "Genere") pour éviter une {@code IllegalArgumentException} PDFBox
 * sur un caractère absent de l'encodage — le fichier Excel, lui, conserve les accents sans
 * restriction (POI écrit directement en UTF-8).
 */
final class PdfTableRenderer {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
  private static final DateTimeFormatter FORMAT_GENERE_LE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
  private static final float MARGE = 40f;
  private static final float HAUTEUR_LIGNE = 16f;
  private static final float TAILLE_TITRE = 14f;
  private static final float TAILLE_ENTETE = 9.5f;
  private static final float TAILLE_CORPS = 8.5f;
  private static final int LARGEUR_COLONNE_MIN_CARACTERES = 6;
  private static final int LARGEUR_COLONNE_MAX_CARACTERES = 38;
  private static final Color COULEUR_ENTETE = new Color(0x1B, 0x2A, 0x41);
  private static final Color COULEUR_BANDE = new Color(0xF7, 0xF7, 0xF4);
  private static final Color COULEUR_TEXTE_ENTETE = Color.WHITE;
  private static final Color COULEUR_TEXTE_CORPS = new Color(0x1B, 0x2A, 0x41);
  private static final Color COULEUR_PIED_DE_PAGE = new Color(0x9C, 0xA3, 0xAF);

  private PdfTableRenderer() {}

  static byte[] rendre(String titre, List<String> entetes, List<List<String>> lignes) {
    try (PDDocument document = new PDDocument()) {
      PDRectangle pagePaysage =
          new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
      PDFont policeNormale = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDFont policeGrasse = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

      float largeurDisponible = pagePaysage.getWidth() - 2 * MARGE;
      float[] largeursColonnes = largeursProportionnelles(entetes, lignes, largeurDisponible);

      EtatRendu etat = new EtatRendu(document, pagePaysage, policeNormale, policeGrasse);
      etat.nouvellePage(titre, lignes.size(), entetes, largeursColonnes);

      for (int index = 0; index < lignes.size(); index++) {
        if (etat.positionY < MARGE + HAUTEUR_LIGNE) {
          etat.fermerFlux();
          etat.nouvellePage(null, lignes.size(), entetes, largeursColonnes);
        }
        etat.ecrireLigne(
            lignes.get(index), largeursColonnes, policeNormale, TAILLE_CORPS, false, index);
      }
      etat.fermerFlux();

      ByteArrayOutputStream sortie = new ByteArrayOutputStream();
      document.save(sortie);
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new ExportGenerationException(e);
    }
  }

  /**
   * Largeur de chaque colonne proportionnelle à la longueur de son contenu (en-tête compris),
   * bornée pour qu'aucune colonne ne devienne illisible (trop étroite) ni n'écrase les autres
   * (texte libre très long, ex. "Motif"/"Détail").
   */
  private static float[] largeursProportionnelles(
      List<String> entetes, List<List<String>> lignes, float largeurDisponible) {
    int nombreColonnes = Math.max(1, entetes.size());
    int[] longueurMax = new int[nombreColonnes];
    for (int colonne = 0; colonne < nombreColonnes; colonne++) {
      String entete = colonne < entetes.size() ? entetes.get(colonne) : null;
      longueurMax[colonne] = entete == null ? 0 : entete.length();
    }
    for (List<String> ligne : lignes) {
      for (int colonne = 0; colonne < nombreColonnes; colonne++) {
        String valeur = colonne < ligne.size() ? ligne.get(colonne) : null;
        int longueur = valeur == null ? 0 : valeur.length();
        if (longueur > longueurMax[colonne]) {
          longueurMax[colonne] = longueur;
        }
      }
    }
    float poidsTotal = 0f;
    float[] poids = new float[nombreColonnes];
    for (int colonne = 0; colonne < nombreColonnes; colonne++) {
      poids[colonne] =
          Math.max(
              LARGEUR_COLONNE_MIN_CARACTERES,
              Math.min(LARGEUR_COLONNE_MAX_CARACTERES, longueurMax[colonne]));
      poidsTotal += poids[colonne];
    }
    float[] largeurs = new float[nombreColonnes];
    for (int colonne = 0; colonne < nombreColonnes; colonne++) {
      largeurs[colonne] = largeurDisponible * (poids[colonne] / poidsTotal);
    }
    return largeurs;
  }

  /** Regroupe l'état mutable de pagination (page courante, flux ouvert, position Y). */
  private static final class EtatRendu {
    private final PDDocument document;
    private final PDRectangle taillePage;
    private final PDFont policeNormale;
    private final PDFont policeGrasse;
    private PDPageContentStream flux;
    private float positionY;
    private int numeroPage;

    EtatRendu(
        PDDocument document, PDRectangle taillePage, PDFont policeNormale, PDFont policeGrasse) {
      this.document = document;
      this.taillePage = taillePage;
      this.policeNormale = policeNormale;
      this.policeGrasse = policeGrasse;
    }

    void nouvellePage(String titre, int nombreLignes, List<String> entetes, float[] largeurs)
        throws IOException {
      PDPage page = new PDPage(taillePage);
      document.addPage(page);
      flux = new PDPageContentStream(document, page);
      numeroPage++;
      positionY = taillePage.getHeight() - MARGE;

      if (titre != null) {
        flux.beginText();
        flux.setNonStrokingColor(COULEUR_TEXTE_CORPS);
        flux.setFont(policeGrasse, TAILLE_TITRE);
        flux.newLineAtOffset(MARGE, positionY);
        flux.showText(versAsciiSimple(titre));
        flux.endText();
        positionY -= TAILLE_TITRE + 10;

        flux.beginText();
        flux.setNonStrokingColor(COULEUR_PIED_DE_PAGE);
        flux.setFont(policeNormale, TAILLE_CORPS);
        flux.newLineAtOffset(MARGE, positionY);
        flux.showText(
            "Genere le "
                + FORMAT_GENERE_LE.format(ZonedDateTime.now(ZONE))
                + " - "
                + nombreLignes
                + " ligne(s)");
        flux.endText();
        positionY -= HAUTEUR_LIGNE;
      }

      ecrireLigne(entetes, largeurs, policeGrasse, TAILLE_ENTETE, true, -1);
    }

    void ecrireLigne(
        List<String> ligne,
        float[] largeurs,
        PDFont police,
        float taille,
        boolean estEntete,
        int indexLigne)
        throws IOException {
      float largeurTotale = 0f;
      for (float largeur : largeurs) {
        largeurTotale += largeur;
      }

      if (estEntete) {
        flux.setNonStrokingColor(COULEUR_ENTETE);
        flux.addRect(MARGE, positionY - HAUTEUR_LIGNE + 3, largeurTotale, HAUTEUR_LIGNE);
        flux.fill();
      } else if (indexLigne % 2 == 1) {
        flux.setNonStrokingColor(COULEUR_BANDE);
        flux.addRect(MARGE, positionY - HAUTEUR_LIGNE + 3, largeurTotale, HAUTEUR_LIGNE);
        flux.fill();
      }

      float x = MARGE;
      for (int colonne = 0; colonne < largeurs.length; colonne++) {
        String valeurBrute = colonne < ligne.size() ? ligne.get(colonne) : "";
        String texte =
            tronquer(
                versAsciiSimple(valeurBrute == null ? "" : valeurBrute),
                police,
                taille,
                largeurs[colonne] - 6);
        flux.beginText();
        flux.setNonStrokingColor(estEntete ? COULEUR_TEXTE_ENTETE : COULEUR_TEXTE_CORPS);
        flux.setFont(police, taille);
        flux.newLineAtOffset(x + 3, positionY - HAUTEUR_LIGNE + 4);
        flux.showText(texte);
        flux.endText();
        x += largeurs[colonne];
      }
      positionY -= HAUTEUR_LIGNE;
    }

    void fermerFlux() throws IOException {
      flux.beginText();
      flux.setNonStrokingColor(COULEUR_PIED_DE_PAGE);
      flux.setFont(policeNormale, TAILLE_CORPS);
      flux.newLineAtOffset(taillePage.getWidth() - MARGE - 40, MARGE / 2);
      flux.showText("Page " + numeroPage);
      flux.endText();
      flux.close();
    }
  }

  private static String tronquer(String texte, PDFont police, float taille, float largeurMax)
      throws IOException {
    if (texte.isEmpty()) {
      return texte;
    }
    String resultat = texte;
    while (largeurTexte(resultat, police, taille) > largeurMax && resultat.length() > 1) {
      resultat = resultat.substring(0, resultat.length() - 1);
    }
    if (!resultat.equals(texte) && resultat.length() > 1) {
      resultat = resultat.substring(0, resultat.length() - 1) + "...";
    }
    return resultat;
  }

  private static float largeurTexte(String texte, PDFont police, float taille) throws IOException {
    return police.getStringWidth(texte) / 1000f * taille;
  }

  private static String versAsciiSimple(String texte) {
    String decompose = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    StringBuilder resultat = new StringBuilder(decompose.length());
    for (int i = 0; i < decompose.length(); i++) {
      char c = decompose.charAt(i);
      resultat.append(c >= 0x20 && c <= 0x7E ? c : '?');
    }
    return resultat.toString();
  }
}
