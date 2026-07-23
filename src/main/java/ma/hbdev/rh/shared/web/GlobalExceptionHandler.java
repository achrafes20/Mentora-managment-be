package ma.hbdev.rh.shared.web;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.auth.AuthException;
import ma.hbdev.rh.auth.DelegationNotFoundException;
import ma.hbdev.rh.auth.UserNotFoundException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestionnaire global des exceptions REST.
 *
 * <p>Toutes les erreurs sont transformées en {@link ApiResponse#error(String)} pour garantir un
 * format de réponse uniforme côté frontend. Les stacktraces ne sont jamais exposées (NFR-SEC).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Erreurs de validation Bean Validation (annotations @Valid sur les DTO). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + " : " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(ApiResponse.error(message));
  }

  /** Violations de contraintes JSR-380 sur les paramètres de méthode. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    String message =
        ex.getConstraintViolations().stream()
            .map(cv -> cv.getPropertyPath() + " : " + cv.getMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(ApiResponse.error(message));
  }

  /** Accès refusé (403) — rôle insuffisant. */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Accès refusé"));
  }

  /** Authentification échouée (401). */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error("Authentification requise"));
  }

  /** AuthException métier (401) — login échoué, verrouillage, token invalide. */
  @ExceptionHandler(AuthException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
  }

  /** Ressource non trouvée (404). */
  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(UserNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  /** Délégation introuvable (404). */
  @ExceptionHandler(DelegationNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleDelegationNotFound(
      DelegationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  /** Requête invalide — e-mail déjà pris, politique de mot de passe, etc. (400). */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
  }

  /** Catch-all — toute exception non gérée → 500 (sans détail interne). */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
    if (responseStatus != null) {
      return ResponseEntity.status(responseStatus.code()).body(ApiResponse.error(ex.getMessage()));
    }
    log.error("Erreur interne non gérée", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("Une erreur interne est survenue"));
  }
}
