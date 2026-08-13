package ma.hbdev.rh.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CsvTableurParserTest {

  private final CsvTableurParser parser = new CsvTableurParser();

  @Test
  void parseUnFichierUtf8Normalement() {
    TableurBrut resultat =
        parser.parser(fichier("Nom,Poste\nFassi,Développeuse frontend\n", StandardCharsets.UTF_8));
    assertThat(resultat.lignes().get(0)).containsExactly("Fassi", "Développeuse frontend");
  }

  // Reproduit un export "CSV (délimité par des virgules)" simple depuis Excel sur un Windows
  // francophone (par opposition à l'option distincte "CSV UTF-8") — l'UTF-8 strict doit échouer sur
  // ces octets et le repli Windows-1252 doit produire le texte correct plutôt que du mojibake.
  @Test
  void repliSurWindows1252SiLesOctetsNeSontPasDeLUtf8Valide() {
    Charset windows1252 = Charset.forName("windows-1252");
    TableurBrut resultat =
        parser.parser(fichier("Nom,Poste\nFassi,Développeuse frontend\n", windows1252));
    assertThat(resultat.lignes().get(0)).containsExactly("Fassi", "Développeuse frontend");
  }

  @Test
  void ignoreLeBomUtf8() {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] contenu = "Nom,Poste\nFassi,Développeuse\n".getBytes(StandardCharsets.UTF_8);
    byte[] avecBom = new byte[bom.length + contenu.length];
    System.arraycopy(bom, 0, avecBom, 0, bom.length);
    System.arraycopy(contenu, 0, avecBom, bom.length, contenu.length);

    TableurBrut resultat =
        parser.parser(new MockMultipartFile("fichier", "import.csv", "text/csv", avecBom));
    assertThat(resultat.entetes()).containsExactly("Nom", "Poste");
  }

  private MockMultipartFile fichier(String contenu, Charset charset) {
    return new MockMultipartFile("fichier", "import.csv", "text/csv", contenu.getBytes(charset));
  }
}
