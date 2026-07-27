package ma.hbdev.rh.shared.file;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fichiers")
class FichierController {

  private final FileStorageService fileStorageService;

  FichierController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
    FichierUploade fichier = fileStorageService.recuperer(id);
    Resource ressource = fileStorageService.charger(id);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(fichier.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + fichier.nomOriginal() + "\"")
        .body(ressource);
  }
}
