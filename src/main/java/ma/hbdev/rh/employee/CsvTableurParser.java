package ma.hbdev.rh.employee;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parse .csv via Apache Commons CSV. Encodage supposé UTF-8 (avec BOM optionnel) — si le fichier
 * réel de la RH s'avère être dans un autre encodage (ex. Windows-1252, courant pour un export Excel
 * français), ce sera une des surprises de format à traiter à la porte de phase 2, pas une hypothèse
 * à deviner maintenant.
 */
@Component
class CsvTableurParser implements TableurParser {

  private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  @Override
  public boolean supporte(MultipartFile fichier) {
    String nom = fichier.getOriginalFilename();
    return nom != null && nom.toLowerCase().endsWith(".csv");
  }

  @Override
  public TableurBrut parser(MultipartFile fichier) {
    try (BufferedReader lecteur =
        new BufferedReader(
            new InputStreamReader(sansBom(fichier.getInputStream()), StandardCharsets.UTF_8))) {
      CSVParser parseur = CSVFormat.DEFAULT.builder().setTrim(true).build().parse(lecteur);
      Iterator<CSVRecord> iterateur = parseur.iterator();
      if (!iterateur.hasNext()) {
        throw new ImportFichierInvalideException("Le fichier CSV est vide");
      }
      List<String> entetes = new ArrayList<>();
      iterateur.next().forEach(entetes::add);

      List<List<String>> lignes = new ArrayList<>();
      while (iterateur.hasNext()) {
        CSVRecord enregistrement = iterateur.next();
        List<String> valeurs = new ArrayList<>();
        enregistrement.forEach(valeurs::add);
        if (valeurs.stream().allMatch(String::isBlank)) {
          continue;
        }
        lignes.add(valeurs);
      }
      return new TableurBrut(entetes, lignes);
    } catch (IOException e) {
      throw new ImportFichierInvalideException("Impossible de lire le fichier CSV", e);
    } catch (RuntimeException e) {
      throw new ImportFichierInvalideException("Fichier CSV invalide : " + e.getMessage(), e);
    }
  }

  private static InputStream sansBom(InputStream source) throws IOException {
    PushbackInputStream flux = new PushbackInputStream(source, BOM_UTF8.length);
    byte[] entete = new byte[BOM_UTF8.length];
    int lus = flux.read(entete);
    if (lus < BOM_UTF8.length
        || entete[0] != BOM_UTF8[0]
        || entete[1] != BOM_UTF8[1]
        || entete[2] != BOM_UTF8[2]) {
      flux.unread(entete, 0, Math.max(lus, 0));
    }
    return flux;
  }
}
