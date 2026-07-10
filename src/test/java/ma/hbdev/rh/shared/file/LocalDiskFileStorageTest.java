package ma.hbdev.rh.shared.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class LocalDiskFileStorageTest {

  @Mock private FichierRepository fichierRepository;

  @TempDir private Path racineStockage;

  private LocalDiskFileStorage televerseur(long tailleMaxMo) {
    return new LocalDiskFileStorage(fichierRepository, racineStockage.toString(), tailleMaxMo);
  }

  @Test
  void rejetteUnFichierVide() {
    MockMultipartFile fichierVide = new MockMultipartFile("fichier", new byte[0]);

    assertThatThrownBy(() -> televerseur(10).televerser(fichierVide, null))
        .isInstanceOf(FichierInvalideException.class);
  }

  @Test
  void rejetteUnTypeMimeNonAutorise() {
    MockMultipartFile fichierExe =
        new MockMultipartFile(
            "fichier", "virus.exe", "application/x-msdownload", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> televerseur(10).televerser(fichierExe, null))
        .isInstanceOf(FichierInvalideException.class)
        .hasMessageContaining("application/x-msdownload");
  }

  @Test
  void rejetteUnFichierTropVolumineux() {
    MockMultipartFile fichierLourd =
        new MockMultipartFile("fichier", "cv.pdf", "application/pdf", new byte[2 * 1024 * 1024]);

    assertThatThrownBy(() -> televerseur(1).televerser(fichierLourd, null))
        .isInstanceOf(FichierInvalideException.class)
        .hasMessageContaining("1 Mo");
  }

  @Test
  void accepteEtStockeUnPdfValide() {
    UUID televersePar = UUID.randomUUID();
    MockMultipartFile cv =
        new MockMultipartFile("fichier", "cv.pdf", "application/pdf", new byte[] {1, 2, 3});
    when(fichierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    FichierUploade resultat = televerseur(10).televerser(cv, televersePar);

    assertThat(resultat.nomOriginal()).isEqualTo("cv.pdf");
    assertThat(resultat.typeMime()).isEqualTo("application/pdf");
    assertThat(resultat.tailleOctets()).isEqualTo(3);
    assertThat(racineStockage.toFile().listFiles()).hasSize(1);
  }

  @Test
  void chargeLeContenuDunFichierStocke() throws Exception {
    Path fichierSurDisque = racineStockage.resolve("test.pdf");
    java.nio.file.Files.writeString(fichierSurDisque, "contenu");
    UUID fichierId = UUID.randomUUID();
    Fichier fichier =
        new Fichier("cv.pdf", fichierSurDisque.toString(), "application/pdf", 7, null);
    when(fichierRepository.findById(fichierId)).thenReturn(java.util.Optional.of(fichier));

    var ressource = televerseur(10).charger(fichierId);

    assertThat(ressource.exists()).isTrue();
    try (var flux = ressource.getInputStream()) {
      assertThat(flux.readAllBytes()).isEqualTo("contenu".getBytes());
    }
  }
}
