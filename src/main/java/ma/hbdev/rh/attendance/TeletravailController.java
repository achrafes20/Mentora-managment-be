package ma.hbdev.rh.attendance;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD admin du planning de télétravail (EF-ATT-08/10). Lecture ouverte à Admin+Manager pour
 * l'encart lecture seule sur la fiche employé.
 */
@RestController
@RequestMapping("/api/employes/{employeId}/teletravail")
public class TeletravailController {

  private final TeletravailService service;

  public TeletravailController(TeletravailService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<PlanningTeletravailReponse>> lister(@PathVariable UUID employeId) {
    return ApiResponse.ok(
        service.listerParEmploye(employeId).stream()
            .map(PlanningTeletravailReponse::depuis)
            .toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PlanningTeletravailReponse> creer(
      @PathVariable UUID employeId, @Valid @RequestBody PlanningTeletravailRequete requete) {
    return ApiResponse.ok(PlanningTeletravailReponse.depuis(service.creer(employeId, requete)));
  }

  @DeleteMapping("/{planningId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> supprimer(@PathVariable UUID employeId, @PathVariable UUID planningId) {
    service.supprimer(planningId);
    return ApiResponse.ok();
  }

  @ExceptionHandler(PlanningTeletravailIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(PlanningTeletravailIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
