package ma.hbdev.rh.shared.export;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Point d'entrée public du module Export (EF-EXP, EF-CFG-05) : chaque module propriétaire construit
 * déjà ses en-têtes/lignes filtrées (mêmes filtres que son écran de liste), et ne demande à ce
 * service que de les sérialiser en Excel ou PDF.
 */
@Service
public class TableauExportService {

  public byte[] generer(
      FormatExport format, String titre, List<String> entetes, List<List<String>> lignes) {
    return switch (format) {
      case xlsx -> ExcelTableRenderer.rendre(titre, entetes, lignes);
      case pdf -> PdfTableRenderer.rendre(titre, entetes, lignes);
    };
  }
}
