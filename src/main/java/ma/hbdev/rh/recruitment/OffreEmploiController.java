package ma.hbdev.rh.recruitment;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** EF-REC-01/11/12. Consultation ouverte à Admin/Manager ; gestion réservée à l'Admin. */
@RestController
@RequestMapping("/api/offres")
public class OffreEmploiController {

  private final OffreEmploiService offreEmploiService;

  public OffreEmploiController(OffreEmploiService offreEmploiService) {
    this.offreEmploiService = offreEmploiService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<OffreEmploiReponse>> lister(
      @RequestParam(required = false) StatutOffreEmploi statut) {
    return ApiResponse.ok(
        offreEmploiService.lister(statut).stream().map(OffreEmploiReponse::depuis).toList());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<OffreEmploiReponse> detail(@PathVariable UUID id) {
    return ApiResponse.ok(OffreEmploiReponse.depuis(offreEmploiService.trouver(id)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<OffreEmploiReponse> creer(@Valid @RequestBody OffreEmploiRequete requete) {
    UUID creePar = CurrentUser.id().orElse(null);
    return ApiResponse.ok(OffreEmploiReponse.depuis(offreEmploiService.creer(requete, creePar)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<OffreEmploiReponse> modifier(
      @PathVariable UUID id, @Valid @RequestBody OffreEmploiRequete requete) {
    return ApiResponse.ok(OffreEmploiReponse.depuis(offreEmploiService.modifier(id, requete)));
  }

  @PostMapping("/{id}/fermer")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<OffreEmploiReponse> fermer(@PathVariable UUID id) {
    return ApiResponse.ok(OffreEmploiReponse.depuis(offreEmploiService.fermer(id)));
  }

  @PostMapping("/{id}/rouvrir")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<OffreEmploiReponse> rouvrir(@PathVariable UUID id) {
    return ApiResponse.ok(OffreEmploiReponse.depuis(offreEmploiService.rouvrir(id)));
  }

  @ExceptionHandler(OffreEmploiIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(OffreEmploiIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
