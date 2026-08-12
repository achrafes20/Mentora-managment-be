package ma.hbdev.rh.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.security.JwtService;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 *   <li>PATCH /api/auth/me/email, /api/auth/me/password – modification de ses propres identifiants,
 *       en libre-service
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

  /**
   * Modification de son propre e-mail. Révoque toutes les sessions actives, y compris celle en
   * cours : une reconnexion avec le nouvel e-mail est nécessaire.
   */
  @PatchMapping("/me/email")
  @Operation(summary = "Modifier son propre e-mail")
  public ResponseEntity<ApiResponse<Void>> changerEmail(
      @Valid @RequestBody ChangeEmailRequest request) {
    UUID userId = CurrentUser.id().orElseThrow(() -> new AuthException("Authentification requise"));
    authService.changeEmail(userId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  /**
   * Modification de son propre mot de passe (mot de passe actuel exigé). Révoque toutes les
   * sessions actives, y compris celle en cours.
   */
  @PatchMapping("/me/password")
  @Operation(summary = "Modifier son propre mot de passe")
  public ResponseEntity<ApiResponse<Void>> changerMotDePasse(
      @Valid @RequestBody ChangePasswordRequest request) {
    UUID userId = CurrentUser.id().orElseThrow(() -> new AuthException("Authentification requise"));
    authService.changePassword(userId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  // EF-AUTH-03 : plus spécifique que le AuthException générique du GlobalExceptionHandler (401 sans
  // data) — porte l'échéance du verrouillage pour que le frontend affiche un décompte, même pattern
  // que KiosqueController#gererCodeInvalide (NFR-UX-02).
  @ExceptionHandler(CompteVerrouilleException.class)
  ResponseEntity<ApiResponse<VerrouillageReponse>> gererCompteVerrouille(
      CompteVerrouilleException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            new ApiResponse<>(
                false,
                new VerrouillageReponse(ex.getVerrouilleJusquA()),
                ex.getMessage(),
                Instant.now()));
  }
}
