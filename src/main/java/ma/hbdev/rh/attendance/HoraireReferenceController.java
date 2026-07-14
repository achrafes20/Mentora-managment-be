package ma.hbdev.rh.attendance;

import jakarta.validation.Valid;
import java.util.List;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de l'horaire de référence (EF-ATT-07) — Admin uniquement. */
@RestController
@RequestMapping("/api/horaires-reference")
public class HoraireReferenceController {

  private final HoraireReferenceService service;

  public HoraireReferenceController(HoraireReferenceService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<HoraireReferenceReponse>> lister() {
    return ApiResponse.ok(
        service.listerTous().stream().map(HoraireReferenceReponse::depuis).toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<HoraireReferenceReponse> creer(
      @Valid @RequestBody HoraireReferenceRequete requete) {
    return ApiResponse.ok(HoraireReferenceReponse.depuis(service.creer(requete)));
  }
}
