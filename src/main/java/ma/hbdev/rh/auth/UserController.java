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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de gestion des comptes utilisateurs — réservés ADMIN (T1.A1).
 *
 * <p>Préfixe : /api/users (déjà protégé par hasRole("ADMIN") dans SecurityConfig)
 *
 * <ul>
 *   <li>GET /api/users – liste tous les comptes
 *   <li>GET /api/users/{id} – détail d'un compte
 *   <li>POST /api/users – création d'un compte admin/manager
 *   <li>PUT /api/users/{id} – mise à jour (rôle, nom, prénom)
 *   <li>PATCH /api/users/{id}/deactivate – désactivation douce
 *   <li>PATCH /api/users/{id}/activate – réactivation
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Utilisateurs", description = "Gestion des comptes (ADMIN uniquement)")
public class UserController {

  private final UserService userService;

  @GetMapping
  @Operation(summary = "Liste tous les comptes")
  public ResponseEntity<ApiResponse<List<UserResponse>>> findAll() {
    return ResponseEntity.ok(ApiResponse.ok(userService.findAll()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Détail d'un compte")
  public ResponseEntity<ApiResponse<UserResponse>> findById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
  }

  @PostMapping
  @Operation(summary = "Création d'un compte admin ou manager")
  public ResponseEntity<ApiResponse<UserResponse>> create(
      @Valid @RequestBody UserCreateRequest request) {
    UserResponse created = userService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Mise à jour du rôle, nom et prénom")
  public ResponseEntity<ApiResponse<UserResponse>> update(
      @PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(userService.update(id, request)));
  }

  @PatchMapping("/{id}/deactivate")
  @Operation(summary = "Désactivation d'un compte (+ révocation des sessions)")
  public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(userService.deactivate(id)));
  }

  @PatchMapping("/{id}/activate")
  @Operation(summary = "Réactivation d'un compte")
  public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(userService.activate(id)));
  }
}
