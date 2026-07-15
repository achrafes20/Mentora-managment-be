package ma.hbdev.rh.employee;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Sélectionne le {@link TableurParser} adapté au fichier fourni (.xlsx/.xls ou .csv). */
@Component
class TableurParserFactory {

  private final List<TableurParser> parseurs;

  TableurParserFactory(List<TableurParser> parseurs) {
    this.parseurs = parseurs;
  }

  TableurBrut parser(MultipartFile fichier) {
    if (fichier == null || fichier.isEmpty()) {
      throw new ImportFichierInvalideException("Fichier vide ou absent");
    }
    return parseurs.stream()
        .filter(p -> p.supporte(fichier))
        .findFirst()
        .orElseThrow(
            () ->
                new ImportFichierInvalideException(
                    "Format de fichier non supporté (formats acceptés : .xlsx, .xls, .csv)"))
        .parser(fichier);
  }
}
