package ma.hbdev.rh.administrative;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demandes-administratives")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
class AdministrativeController {

  private final AdministrativeService service;

  AdministrativeController(AdministrativeService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<PagedResponse<DemandeAdministrativeReponse>> lister(
      @RequestParam(required = false) UUID employeId,
      @RequestParam(required = false) TypeDemandeAdministrative type,
      @RequestParam(required = false) StatutDemandeAdministrative statut,
      @RequestParam(required = false) LocalDate debut,
      @RequestParam(required = false) LocalDate fin,
      Pageable pageable) {
    return ApiResponse.ok(
        PagedResponse.of(service.lister(employeId, type, statut, debut, fin, pageable)));
  }

  @PostMapping
  ApiResponse<DemandeAdministrativeReponse> creer(
      @Valid @RequestBody DemandeAdministrativeRequete requete) {
    return ApiResponse.ok(service.creer(requete));
  }

  @PatchMapping("/{id}/approuver")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<DemandeAdministrativeReponse> approuver(@PathVariable UUID id) {
    return ApiResponse.ok(service.approuver(id));
  }

  @PatchMapping("/{id}/rejeter")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<DemandeAdministrativeReponse> rejeter(@PathVariable UUID id) {
    return ApiResponse.ok(service.rejeter(id));
  }

  @PatchMapping("/{id}/annuler")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<DemandeAdministrativeReponse> annuler(@PathVariable UUID id) {
    return ApiResponse.ok(service.annuler(id));
  }

  @GetMapping("/employes/{employeId}/solde")
  ApiResponse<SoldeCongeReponse> solde(@PathVariable UUID employeId) {
    return ApiResponse.ok(service.solde(employeId));
  }

  @GetMapping("/employes/{employeId}/mouvements")
  ApiResponse<List<MouvementCongeReponse>> mouvements(@PathVariable UUID employeId) {
    return ApiResponse.ok(service.mouvements(employeId));
  }

  @GetMapping("/jours-feries")
  ApiResponse<List<JourFerieReponse>> joursFeries() {
    return ApiResponse.ok(service.joursFeries());
  }

  @PostMapping("/jours-feries")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<JourFerieReponse> creerJourFerie(@Valid @RequestBody JourFerieRequete requete) {
    return ApiResponse.ok(service.creerJourFerie(requete));
  }

  @DeleteMapping("/jours-feries/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<Void> supprimerJourFerie(@PathVariable UUID id) {
    service.supprimerJourFerie(id);
    return ApiResponse.ok();
  }
}
