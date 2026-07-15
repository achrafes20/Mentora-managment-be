package ma.hbdev.rh.attendance;

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
import org.springframework.web.bind.annotation.RestController;

/** Écran anomalies de pointage (EF-ATT-04/05) — marquage résolu. */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalieController {

  private final AnomalieService anomalieService;

  public AnomalieController(AnomalieService anomalieService) {
    this.anomalieService = anomalieService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<AnomaliePointageReponse>> lister(
      @RequestParam(required = false) Boolean resolue, Pageable pageable) {
    var page = anomalieService.lister(resolue, pageable);
    return ApiResponse.ok(PagedResponse.of(page.map(AnomaliePointageReponse::depuis)));
  }

  @PostMapping("/{id}/resoudre")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<AnomaliePointageReponse> marquerResolue(@PathVariable UUID id) {
    return ApiResponse.ok(AnomaliePointageReponse.depuis(anomalieService.marquerResolue(id)));
  }

  @ExceptionHandler(AnomalieIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(AnomalieIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
