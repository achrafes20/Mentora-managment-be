package ma.hbdev.rh.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(name = "Notifications", description = "Centre de notifications de l'utilisateur courant")
public class NotificationController {

  private final NotificationService service;

  @GetMapping
  @Operation(summary = "Lister mes notifications non archivees")
  public ApiResponse<PagedResponse<NotificationReponse>> lister(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(
        PagedResponse.of(service.lister(page, size).map(NotificationReponse::depuis)));
  }

  @GetMapping("/non-lues/count")
  @Operation(summary = "Compter mes notifications non lues")
  public ApiResponse<Map<String, Long>> compterNonLues() {
    return ApiResponse.ok(Map.of("count", service.compterNonLues()));
  }

  @PatchMapping("/{id}/lire")
  @Operation(summary = "Marquer une de mes notifications comme lue")
  public ApiResponse<NotificationReponse> marquerLue(@PathVariable UUID id) {
    return ApiResponse.ok(NotificationReponse.depuis(service.marquerLue(id)));
  }

  @PatchMapping("/lire-toutes")
  @Operation(summary = "Marquer toutes mes notifications comme lues")
  public ApiResponse<Map<String, Integer>> marquerToutesLues() {
    return ApiResponse.ok(Map.of("nombreMisAJour", service.marquerToutesLues()));
  }
}
