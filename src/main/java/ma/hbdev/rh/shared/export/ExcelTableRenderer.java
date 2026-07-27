package ma.hbdev.rh.shared.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Rendu Excel (.xlsx) générique à partir d'en-têtes/lignes déjà formatées en texte.
 *
 * <p>Largeur de colonne calculée à partir de la longueur du contenu plutôt que via {@code
 * Sheet#autoSizeColumn} (celui-ci dépend de métriques de police AWT — fragile en environnement
 * conteneurisé sans configuration de polices).
 */
final class ExcelTableRenderer {

  private static final int LARGEUR_MIN_CARACTERES = 10;
  private static final int LARGEUR_MAX_CARACTERES = 60;

  private ExcelTableRenderer() {}

  static byte[] rendre(String titreFeuille, List<String> entetes, List<List<String>> lignes) {
    try (XSSFWorkbook classeur = new XSSFWorkbook()) {
      Sheet feuille = classeur.createSheet(nomFeuilleValide(titreFeuille));

      CellStyle styleEntete = styleEntete(classeur);
      Row ligneEntete = feuille.createRow(0);
      int[] largeurMax = new int[entetes.size()];
      for (int colonne = 0; colonne < entetes.size(); colonne++) {
        Cell cellule = ligneEntete.createCell(colonne);
        cellule.setCellValue(entetes.get(colonne));
        cellule.setCellStyle(styleEntete);
        largeurMax[colonne] = entetes.get(colonne) == null ? 0 : entetes.get(colonne).length();
      }

      for (int indexLigne = 0; indexLigne < lignes.size(); indexLigne++) {
        List<String> ligne = lignes.get(indexLigne);
        Row ligneExcel = feuille.createRow(indexLigne + 1);
        for (int colonne = 0; colonne < entetes.size(); colonne++) {
          String valeur = colonne < ligne.size() ? ligne.get(colonne) : "";
          ligneExcel.createCell(colonne).setCellValue(valeur == null ? "" : valeur);
          largeurMax[colonne] = Math.max(largeurMax[colonne], valeur == null ? 0 : valeur.length());
        }
      }

      for (int colonne = 0; colonne < entetes.size(); colonne++) {
        int largeur =
            Math.min(LARGEUR_MAX_CARACTERES, Math.max(LARGEUR_MIN_CARACTERES, largeurMax[colonne]));
        feuille.setColumnWidth(colonne, (largeur + 2) * 256);
      }

      ByteArrayOutputStream sortie = new ByteArrayOutputStream();
      classeur.write(sortie);
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new ExportGenerationException(e);
    }
  }

  private static CellStyle styleEntete(XSSFWorkbook classeur) {
    Font police = classeur.createFont();
    police.setBold(true);
    CellStyle style = classeur.createCellStyle();
    style.setFont(police);
    return style;
  }

  private static String nomFeuilleValide(String titre) {
    String nettoye = titre.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
    return nettoye.isBlank() ? "Export" : nettoye.substring(0, Math.min(31, nettoye.length()));
  }
}
