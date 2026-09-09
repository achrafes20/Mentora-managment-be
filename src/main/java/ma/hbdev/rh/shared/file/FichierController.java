package ma.hbdev.rh.shared.file;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Générique par UUID de fichier, sans lien avec l'entité RH qui le référence (employé, demande,
// identité entreprise) : impossible de vérifier ici un périmètre plus fin qu'Admin/Manager — les
// écrans réels passent par des endpoints dédiés (photo employé, justificatif de demande...) qui,
// eux, appliquent le vrai contrôle métier ; celui-ci reste en filet de sécurité générique. Retiré
// de
// PUBLIC_PATHS (SecurityConfig) le 2026-08-27 : entièrement public à l'origine, sans qu'aucun
// appelant
// (frontend ou génération de document) ne l'utilise réellement — les documents générés embarquent
// leurs fichiers en base64 côté serveur (CertificatGenerator#fichierImgTag), pas via cet endpoint.
@RestController
@RequestMapping("/api/fichiers")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
            HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fichier.nomOriginal() + "\"")
        .body(ressource);
  }
}
