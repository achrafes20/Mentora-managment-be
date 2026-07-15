package ma.hbdev.rh.employee;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Parse .xlsx/.xls (première feuille uniquement) via Apache POI. */
@Component
class ExcelTableurParser implements TableurParser {

  @Override
  public boolean supporte(MultipartFile fichier) {
    String nom = fichier.getOriginalFilename();
    return nom != null
        && (nom.toLowerCase().endsWith(".xlsx") || nom.toLowerCase().endsWith(".xls"));
  }

  @Override
  public TableurBrut parser(MultipartFile fichier) {
    DataFormatter formateur = new DataFormatter();
    try (Workbook classeur = WorkbookFactory.create(fichier.getInputStream())) {
      Sheet feuille = classeur.getSheetAt(0);
      if (feuille == null || feuille.getPhysicalNumberOfRows() == 0) {
        throw new ImportFichierInvalideException(
            "Le fichier ne contient aucune feuille exploitable");
      }
      int derniereColonne = feuille.getRow(feuille.getFirstRowNum()).getLastCellNum();

      List<String> entetes = new ArrayList<>();
      Row ligneEntetes = feuille.getRow(feuille.getFirstRowNum());
      for (int c = 0; c < derniereColonne; c++) {
        entetes.add(formateur.formatCellValue(ligneEntetes.getCell(c)).trim());
      }

      List<List<String>> lignes = new ArrayList<>();
      for (int r = feuille.getFirstRowNum() + 1; r <= feuille.getLastRowNum(); r++) {
        Row ligne = feuille.getRow(r);
        if (ligne == null || estLigneVide(ligne, formateur, derniereColonne)) {
          continue;
        }
        List<String> valeurs = new ArrayList<>();
        for (int c = 0; c < derniereColonne; c++) {
          valeurs.add(formateur.formatCellValue(ligne.getCell(c)).trim());
        }
        lignes.add(valeurs);
      }
      return new TableurBrut(entetes, lignes);
    } catch (IOException e) {
      throw new ImportFichierInvalideException("Impossible de lire le fichier Excel", e);
    } catch (RuntimeException e) {
      throw new ImportFichierInvalideException(
          "Fichier Excel invalide ou corrompu : " + e.getMessage(), e);
    }
  }

  private boolean estLigneVide(Row ligne, DataFormatter formateur, int derniereColonne) {
    for (int c = 0; c < derniereColonne; c++) {
      if (!formateur.formatCellValue(ligne.getCell(c)).isBlank()) {
        return false;
      }
    }
    return true;
  }
}
