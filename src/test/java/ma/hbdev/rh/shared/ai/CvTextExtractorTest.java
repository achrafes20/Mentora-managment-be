package ma.hbdev.rh.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/**
 * Régression sur un bug réel trouvé en conditions réelles : PDFBox extrait certains PDF en Unicode
 * NFD ("é" décomposé en "e" + accent combinant séparé) plutôt que NFC — repéré sur un vrai CV où
 * l'IA produisait une justification incohérente et un mot-clé aberrant à cause de ce découpage de
 * tokens inhabituel.
 */
class CvTextExtractorTest {

  @Test
  void normaliseUnTexteDecomposeNfdEnFormeComposeeNfc() {
    String motCompose = "Étudiant à l'ENSA Tétouan"; // forme normale (NFC)
    String motDecompose = Normalizer.normalize(motCompose, Normalizer.Form.NFD);
    // Vérifie que le test construit bien un cas décomposé (sinon le test ne prouverait rien).
    assertThat(motDecompose).isNotEqualTo(motCompose);

    String resultat = CvTextExtractor.normaliser(motDecompose);

    assertThat(resultat).isEqualTo(motCompose);
    assertThat(Normalizer.isNormalized(resultat, Normalizer.Form.NFC)).isTrue();
  }

  @Test
  void neModifiePasUnTexteDejaEnFormeComposeeNfc() {
    String texte = "Compétences techniques : Java, Développement Backend";
    assertThat(CvTextExtractor.normaliser(texte)).isEqualTo(texte);
  }
}
