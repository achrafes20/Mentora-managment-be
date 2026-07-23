package ma.hbdev.rh.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de délégation d'approbation — réservés ADMIN (EF-AUTH-11→15), sauf {@link
 * #maDelegation()}.
 *
 * <p>Préfixe : /api/delegations
 *
 * <ul>
 *   <li>GET /api/delegations – historique complet (le plus récent en premier)
 *   <li>GET /api/delegations/moi – ma délégation active en tant que délégué, si j'en ai une
 *   <li>POST /api/delegations – désignation d'un délégué temporaire
 *   <li>POST /api/delegations/{id}/revoquer – révocation manuelle
 * </ul>
 */
@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Délégations", description = "Délégation temporaire d'approbation (ADMIN uniquement)")
public class DelegationController {

  private final DelegationService delegationService;

  @GetMapping
  @Operation(summary = "Historique complet des délégations")
  public ResponseEntity<ApiResponse<List<DelegationReponse>>> lister() {
    return ResponseEntity.ok(ApiResponse.ok(delegationService.lister()));
  }

  /**
   * Ouvert à tout utilisateur authentifié (Admin ou Manager) — c'est le seul moyen pour le frontend
   * de savoir si l'utilisateur courant est actuellement un délégué actif (pour afficher les actions
   * d'approbation/décision), sans lui exposer l'historique complet réservé à l'Admin via {@link
   * #lister()}.
   */
  @GetMapping("/moi")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Ma délégation active en tant que délégué (ou aucune)")
  public ResponseEntity<ApiResponse<DelegationReponse>> maDelegation() {
    return ResponseEntity.ok(
        ApiResponse.ok(delegationService.delegationActivePourUtilisateurCourant().orElse(null)));
  }

  @PostMapping
  @Operation(summary = "Désignation d'un délégué temporaire")
  public ResponseEntity<ApiResponse<DelegationReponse>> creer(
      @Valid @RequestBody DelegationCreationRequete requete) {
    DelegationReponse created = delegationService.creer(requete);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
  }

  @PostMapping("/{id}/revoquer")
  @Operation(summary = "Révocation manuelle d'une délégation active")
  public ResponseEntity<ApiResponse<DelegationReponse>> revoquer(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(delegationService.revoquer(id)));
  }
}
