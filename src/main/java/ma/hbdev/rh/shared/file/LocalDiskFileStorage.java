package ma.hbdev.rh.shared.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Implémentation dev/on-prem de {@link FileStorageService} : écrit sur un disque local. */
@Service
class LocalDiskFileStorage implements FileStorageService {

  // NFR-SEC-07 — types autorisés pour CV / pièces jointes employé / documents libres.
  private static final Set<String> TYPES_MIME_AUTORISES =
      Set.of(
          "application/pdf",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "image/jpeg",
          "image/png");

  private final FichierRepository fichierRepository;
  private final Path racineStockage;
  private final long tailleMaxOctets;

  LocalDiskFileStorage(
      FichierRepository fichierRepository,
      @Value("${app.file-storage.path}") String cheminRacine,
      @Value("${app.file-storage.max-size-mb}") long tailleMaxMo) {
    this.fichierRepository = fichierRepository;
    this.racineStockage = Path.of(cheminRacine);
    this.tailleMaxOctets = tailleMaxMo * 1024 * 1024;
  }

  @Override
  public FichierUploade televerser(MultipartFile fichier, UUID televersePar) {
    valider(fichier);

    String nomStockage = UUID.randomUUID() + extension(fichier.getOriginalFilename());
    Path destination = racineStockage.resolve(nomStockage);
    try {
      Files.createDirectories(racineStockage);
      fichier.transferTo(destination);
    } catch (IOException e) {
      throw new UncheckedIOException("Échec de l'écriture du fichier sur disque", e);
    }

    Fichier sauvegarde =
        fichierRepository.save(
            new Fichier(
                fichier.getOriginalFilename(),
                destination.toString(),
                fichier.getContentType(),
                fichier.getSize(),
                televersePar));

    return new FichierUploade(
        sauvegarde.getId(),
        sauvegarde.getNomOriginal(),
        sauvegarde.getTypeMime(),
        sauvegarde.getTailleOctets());
  }

  @Override
  public FichierUploade recuperer(UUID fichierId) {
    Fichier fichier =
        fichierRepository
            .findById(fichierId)
            .orElseThrow(
                () -> new IllegalStateException("Fichier référencé introuvable : " + fichierId));
    return new FichierUploade(
        fichier.getId(),
        fichier.getNomOriginal(),
        fichier.getTypeMime(),
        fichier.getTailleOctets());
  }

  @Override
  public Resource charger(UUID fichierId) {
    Fichier fichier =
        fichierRepository
            .findById(fichierId)
            .orElseThrow(
                () -> new IllegalStateException("Fichier référencé introuvable : " + fichierId));
    try {
      return new UrlResource(Path.of(fichier.getCheminStockage()).toUri());
    } catch (MalformedURLException e) {
      throw new UncheckedIOException(new IOException(e));
    }
  }

  private void valider(MultipartFile fichier) {
    if (fichier == null || fichier.isEmpty()) {
      throw new FichierInvalideException("Fichier vide ou absent");
    }
    if (!TYPES_MIME_AUTORISES.contains(fichier.getContentType())) {
      throw new FichierInvalideException(
          "Type de fichier non autorisé : " + fichier.getContentType());
    }
    if (fichier.getSize() > tailleMaxOctets) {
      throw new FichierInvalideException(
          "Fichier trop volumineux (max " + (tailleMaxOctets / 1024 / 1024) + " Mo)");
    }
  }

  private static String extension(String nomOriginal) {
    if (nomOriginal == null) {
      return "";
    }
    int pointIndex = nomOriginal.lastIndexOf('.');
    return pointIndex >= 0 ? nomOriginal.substring(pointIndex) : "";
  }
}
