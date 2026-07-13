package ma.hbdev.rh.employee;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * EF-EMP-10. Consultation ouverte à Admin/Manager (référence bénigne, nécessaire au Manager pour
 * résoudre les noms de département affichés côté fiche employé) ; création/modification/
 * (dés)activation réservées à l'Admin (T1.C1).
 */
@RestController
@RequestMapping("/api/departements")
public class DepartementController {

  private final DepartementService departementService;

  public DepartementController(DepartementService departementService) {
    this.departementService = departementService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<DepartementReponse>> lister() {
    return ApiResponse.ok(
        departementService.lister().stream().map(DepartementReponse::depuis).toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<DepartementReponse> creer(@Valid @RequestBody DepartementRequete requete) {
    return ApiResponse.ok(DepartementReponse.depuis(departementService.creer(requete)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<DepartementReponse> modifier(
      @PathVariable UUID id, @Valid @RequestBody DepartementRequete requete) {
    return ApiResponse.ok(DepartementReponse.depuis(departementService.modifier(id, requete)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> desactiver(@PathVariable UUID id) {
    departementService.desactiver(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/activer")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<DepartementReponse> activer(@PathVariable UUID id) {
    return ApiResponse.ok(DepartementReponse.depuis(departementService.activer(id)));
  }

  @ExceptionHandler(DepartementIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(DepartementIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DepartementNomDejaUtiliseException.class)
  ResponseEntity<ApiResponse<Void>> gererNomDejaUtilise(DepartementNomDejaUtiliseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(DepartementADesEmployesActifsException.class)
  ResponseEntity<ApiResponse<Void>> gererEmployesActifs(DepartementADesEmployesActifsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }
}
