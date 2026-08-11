package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des activations kiosque (NFR-UX-02) — Admin, ou délégué actif (EF-AUTH-11/12), au même
 * titre que le reste des droits délégables. Distinct de {@link KiosqueController}, qui reste public
 * : ces endpoints exigent une session JWT, contrairement au scan/activation côté appareil.
 */
@RestController
@RequestMapping("/api/kiosque/activations")
@PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
class KiosqueActivationController {

  private final KiosqueActivationService activationService;

  KiosqueActivationController(KiosqueActivationService activationService) {
    this.activationService = activationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<CodeGenereReponse> genererCode() {
    return ApiResponse.ok(CodeGenereReponse.depuis(activationService.genererCode()));
  }

  // EF-ATT-16 : code lié à un employé précis — l'appareil qui l'active devient son téléphone
  // personnel de pointage (voir KiosqueController#scannerPersonnel), pas un kiosque partagé.
  @PostMapping("/personnel/{employeId}")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<CodeGenereReponse> genererCodePersonnel(@PathVariable UUID employeId) {
    return ApiResponse.ok(
        CodeGenereReponse.depuis(activationService.genererCodePersonnel(employeId)));
  }

  @GetMapping
  ApiResponse<List<KiosqueActivationReponse>> lister() {
    return ApiResponse.ok(
        activationService.lister().stream().map(KiosqueActivationReponse::depuis).toList());
  }

  @PostMapping("/{id}/revoquer")
  ApiResponse<Void> revoquer(@PathVariable UUID id) {
    activationService.revoquer(id);
    return ApiResponse.ok();
  }

  @ExceptionHandler(KiosqueActivationIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(KiosqueActivationIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }
}
