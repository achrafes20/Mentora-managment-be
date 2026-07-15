package ma.hbdev.rh.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ColonneMatcherTest {

  @Test
  void suggereParCorrespondanceExacteApresNormalisation() {
    List<String> entetes = List.of("Nom", "Prénom", "E-mail");
    List<ImportChampSpec> champs = ImportChampsRegistry.champsPour(ImportCible.EMPLOYES);

    Map<String, Integer> mapping = ColonneMatcher.suggererMapping(entetes, champs);

    assertThat(mapping)
        .containsEntry("nom", 0)
        .containsEntry("prenom", 1)
        .containsEntry("email", 2);
  }

  @Test
  void neReutilisePasDeuxFoisLaMemeColonne() {
    // "departement" est un synonyme partagé par plusieurs libellés candidats mais une seule
    // colonne source porte ce nom : elle ne doit être assignée qu'à un seul champ cible.
    List<String> entetes = List.of("Departement");
    List<ImportChampSpec> champs = ImportChampsRegistry.champsPour(ImportCible.EMPLOYES);

    Map<String, Integer> mapping = ColonneMatcher.suggererMapping(entetes, champs);

    assertThat(mapping.values()).containsOnlyOnce(0);
    assertThat(mapping).hasSize(1);
  }

  @Test
  void ignoreLesEntetesSansCorrespondance() {
    List<String> entetes = List.of("Colonne mystere");
    List<ImportChampSpec> champs = ImportChampsRegistry.champsPour(ImportCible.DEPARTEMENTS);

    Map<String, Integer> mapping = ColonneMatcher.suggererMapping(entetes, champs);

    assertThat(mapping).isEmpty();
  }

  @Test
  void normaliseAccentsCasseEtPonctuation() {
    assertThat(ColonneMatcher.normaliser("Date d'Embauche")).isEqualTo("date d embauche");
    assertThat(ColonneMatcher.normaliser("Prénom")).isEqualTo("prenom");
    assertThat(ColonneMatcher.normaliser(null)).isEmpty();
  }
}
