package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * EF-EMP-07 — import en masse Excel/CSV (départements, employés, soldes de congés initiaux).
 * Réservé à l'Admin RH (action de migration en masse, même logique que les autres écritures EMP).
 */
@RestController
@RequestMapping("/api/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

  private final ImportService importService;
  private final ObjectMapper objectMapper;

  public ImportController(ImportService importService, ObjectMapper objectMapper) {
    this.importService = importService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/previsualiser")
  public ApiResponse<ImportApercuReponse> previsualiser(
      @RequestParam ImportCible cible, @RequestPart MultipartFile fichier) {
    return ApiResponse.ok(importService.previsualiser(fichier, cible));
  }

  @PostMapping("/analyser")
  public ApiResponse<ImportRapportReponse> analyser(
      @RequestParam ImportCible cible,
      @RequestParam(defaultValue = "ECRASER") StrategieDoublon strategieDoublon,
      @RequestPart MultipartFile fichier,
      @RequestPart Map<String, Integer> mapping) {
    return ApiResponse.ok(
        ImportRapportReponse.depuis(
            importService.analyser(fichier, cible, mapping, strategieDoublon), objectMapper));
  }

  @PostMapping("/executer")
  public ApiResponse<ImportRapportReponse> executer(
      @RequestParam ImportCible cible,
      @RequestParam(defaultValue = "ECRASER") StrategieDoublon strategieDoublon,
      @RequestPart MultipartFile fichier,
      @RequestPart Map<String, Integer> mapping) {
    return ApiResponse.ok(
        ImportRapportReponse.depuis(
            importService.executerReellement(fichier, cible, mapping, strategieDoublon),
            objectMapper));
  }

  @GetMapping("/historique")
  public ApiResponse<PagedResponse<ImportLotReponse>> historique(Pageable pageable) {
    return ApiResponse.ok(
        PagedResponse.of(importService.historique(pageable).map(ImportLotReponse::depuis)));
  }

  @GetMapping("/historique/{lotId}")
  public ApiResponse<ImportRapportReponse> detailLot(@PathVariable UUID lotId) {
    ImportLot lot = importService.trouverLot(lotId);
    return ApiResponse.ok(ImportRapportReponse.depuis(lot, importService.lignesDuLot(lotId)));
  }

  @ExceptionHandler(ImportFichierInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererFichierInvalide(ImportFichierInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ImportMappingInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererMappingInvalide(ImportMappingInvalideException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ImportLotIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererLotIntrouvable(ImportLotIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
