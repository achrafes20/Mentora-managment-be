package ma.hbdev.rh.employee;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parse .csv via Apache Commons CSV. UTF-8 essayé en premier, strictement (BOM optionnel) ; si les
 * octets ne sont pas de l'UTF-8 valide, repli sur Windows-1252 — encodage par défaut d'un export
 * "CSV (délimité par des virgules)" simple depuis Excel sur un Windows francophone (l'option
 * distincte "CSV UTF-8" d'Excel produit, elle, du vrai UTF-8 ; rien dans le fichier ne dit laquelle
 * a été utilisée). Windows-1252 ne peut pas servir de détecteur : il décode n'importe quelle suite
 * d'octets sans jamais échouer, il ne peut donc être qu'un repli, jamais le premier essai.
 */
@Component
class CsvTableurParser implements TableurParser {

  private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  @Override
  public boolean supporte(MultipartFile fichier) {
    String nom = fichier.getOriginalFilename();
    return nom != null && nom.toLowerCase().endsWith(".csv");
  }

  @Override
  public TableurBrut parser(MultipartFile fichier) {
    try (BufferedReader lecteur = new BufferedReader(new StringReader(decoder(fichier)))) {
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

  private static String decoder(MultipartFile fichier) throws IOException {
    byte[] octets = sansBom(fichier.getBytes());
    CharsetDecoder decodeurUtf8 =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decodeurUtf8.decode(ByteBuffer.wrap(octets)).toString();
    } catch (CharacterCodingException e) {
      return new String(octets, WINDOWS_1252);
    }
  }

  private static byte[] sansBom(byte[] octets) {
    if (octets.length >= BOM_UTF8.length && Arrays.equals(octets, 0, 3, BOM_UTF8, 0, 3)) {
      return Arrays.copyOfRange(octets, BOM_UTF8.length, octets.length);
    }
    return octets;
  }
}
