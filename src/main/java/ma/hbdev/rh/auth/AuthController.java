package ma.hbdev.rh.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.security.JwtService;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'authentification (T1.A1).
 *
 * <p>Tous publics sauf /logout (nécessite un jeton valide).
 *
 * <ul>
 *   <li>POST /api/auth/login – EF-AUTH-01
 *   <li>POST /api/auth/logout – EF-AUTH-05
 *   <li>POST /api/auth/forgot-password – EF-AUTH-06
 *   <li>POST /api/auth/reset-password – EF-AUTH-07/08
 *   <li>GET /api/auth/me – profil de l'utilisateur connecté
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentification", description = "Login, logout et gestion des mots de passe")
public class AuthController {

  private final AuthService authService;
  private final JwtService jwtService;
  private final UserRepository userRepository;

  /** Health-check minimal (non sécurisé). */
  @GetMapping("/health")
  @Operation(summary = "Health check")
  public ResponseEntity<ApiResponse<String>> health() {
    return ResponseEntity.ok(ApiResponse.ok("OK"));
  }

  /** EF-AUTH-01 : Login email + mot de passe → JWT + profil. */
  @PostMapping("/login")
  @Operation(summary = "Connexion utilisateur")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /** EF-AUTH-05 : Logout — révoque la session associée au jeton Bearer. */
  @PostMapping("/logout")
  @Operation(summary = "Déconnexion")
  public ResponseEntity<ApiResponse<Void>> logout(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      String tokenHash = jwtService.hashToken(token);
      authService.logout(tokenHash);
    }
    return ResponseEntity.ok(ApiResponse.ok());
  }

  /** EF-AUTH-06 : Demande de réinitialisation (envoie le lien par e-mail via n8n). */
  @PostMapping("/forgot-password")
  @Operation(summary = "Demande de réinitialisation de mot de passe")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request.email());
    // Toujours retourner 200 pour ne pas révéler si l'e-mail existe
    return ResponseEntity.ok(ApiResponse.ok());
  }

  /** EF-AUTH-07/08 : Réinitialisation effective du mot de passe. */
  @PostMapping("/reset-password")
  @Operation(summary = "Réinitialisation du mot de passe")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  /** Retourne le profil de l'utilisateur connecté (depuis le contexte de sécurité). */
  @GetMapping("/me")
  @Operation(summary = "Profil de l'utilisateur connecté")
  public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthException("Utilisateur introuvable"));
    return ResponseEntity.ok(ApiResponse.ok(UserResponse.fromUser(user)));
  }
}
