package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** EF-ATT-11 — seuil d'anomalies avant notification d'alerte. Modification réservée Admin. */
@RestController
@RequestMapping("/api/politique-anomalies")
public class PolitiqueAnomaliesController {

  private final PolitiqueAnomaliesService service;

  public PolitiqueAnomaliesController(PolitiqueAnomaliesService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PolitiqueAnomaliesReponse> obtenir() {
    return ApiResponse.ok(service.obtenir());
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PolitiqueAnomaliesReponse> modifier(
      @Valid @RequestBody PolitiqueAnomaliesRequete requete) {
    return ApiResponse.ok(service.modifier(requete));
  }
}
