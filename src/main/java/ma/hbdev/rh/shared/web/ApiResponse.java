package ma.hbdev.rh.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Enveloppe de réponse unifiée pour toutes les API REST.
 *
 * <p>Format systématique : {"success": true/false, "data": ..., "error": ..., "timestamp": ...}
 *
 * <p>Les contrôleurs retournent {@code ApiResponse.ok(data)} ou {@code ApiResponse.error(msg)}. Le
 * {@link GlobalExceptionHandler} produit aussi des {@code ApiResponse.error} pour toutes les
 * exceptions non gérées.
 *
 * @param <T> type du payload de données
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String error, Instant timestamp) {

  /** Réponse de succès avec données. */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, Instant.now());
  }

  /** Réponse de succès sans données (ex. DELETE, actions sans retour). */
  public static <Void> ApiResponse<Void> ok() {
    return new ApiResponse<>(true, null, null, Instant.now());
  }

  /** Réponse d'erreur avec message. */
  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, null, message, Instant.now());
  }
}
