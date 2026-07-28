package ma.hbdev.rh.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EF-DASH-01/02/03/04/05 : endpoint lecture seule du tableau de bord.
 *
 * <ul>
 *   <li>GET /api/dashboard/stats → Admin (toutes métriques globales, EF-DASH-01)
 *   <li>GET /api/dashboard/stats/manager → Manager (périmètre réduit, EF-DASH-02)
 * </ul>
 *
 * Données rafraîchies à chaque appel — aucune mise en cache (EF-DASH-04).
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Agrégats lecture seule du tableau de bord (EF-DASH-01/02)")
public class DashboardController {

  private final DashboardService dashboardService;

  /**
   * EF-DASH-01 : statistiques Admin — toutes équipes confondues.
   *
   * <p>Accessible également à un délégué actif (EF-AUTH-12 exclut la gestion des comptes et la
   * configuration, pas la lecture du tableau de bord).
   */
  @GetMapping("/stats")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  @Operation(summary = "Statistiques tableau de bord Admin (EF-DASH-01)")
  public ResponseEntity<ApiResponse<DashboardStatsReponse>> statsAdmin() {
    return ResponseEntity.ok(ApiResponse.ok(dashboardService.statsAdmin()));
  }

  /**
   * EF-DASH-02 : statistiques Manager — restreintes au département géré.
   *
   * <p>Accessible à un Manager (rôle brut) ou à un Admin qui veut voir la vue Manager.
   */
  @GetMapping("/stats/manager")
  @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
  @Operation(summary = "Statistiques tableau de bord Manager (EF-DASH-02)")
  public ResponseEntity<ApiResponse<DashboardStatsReponse>> statsManager() {
    UUID managerId =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    return ResponseEntity.ok(ApiResponse.ok(dashboardService.statsManager(managerId)));
  }
}
