package ma.hbdev.rh.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Accès à l'utilisateur authentifié courant, consommable depuis n'importe quel module (T1.C1) —
 * évite à chaque module de requêter directement le module auth pour résoudre "qui est connecté".
 */
public final class CurrentUser {

  private CurrentUser() {}

  /** Id de l'utilisateur connecté (résolu depuis la session applicative par le filtre JWT). */
  public static Optional<UUID> id() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getDetails() instanceof AuthenticatedUserDetails details) {
      return Optional.of(details.userId());
    }
    return Optional.empty();
  }

  /**
   * Vrai si l'utilisateur connecté porte le rôle donné (ex. {@code "MANAGER"}, {@code "ADMIN"}).
   */
  public static boolean hasRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    String authority = "ROLE_" + role.toUpperCase();
    return authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals(authority));
  }
}
