package ma.hbdev.rh.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Sans ce gestionnaire, un utilisateur authentifié mais dont le rôle ne satisfait pas une règle
 * posée dans {@code SecurityConfig} (ex. {@code requestMatchers("/api/users/**").hasRole("ADMIN")})
 * est rejeté par le comportement par défaut de Spring Security : 403 à corps vide. Les refus posés
 * via {@code @PreAuthorize} sur un contrôleur, eux, lèvent la même exception mais à l'intérieur du
 * dispatch Spring MVC — {@code GlobalExceptionHandler#handleAccessDenied} les intercepte déjà
 * correctement ; celui-ci ne couvre que le cas manqué (refus au niveau de la chaîne de filtres,
 * avant le dispatch). Même message, pour un texte cohérent des deux côtés.
 */
@Component
class ApiAccessDeniedHandler implements AccessDeniedHandler {

  private final SecurityResponseWriter responseWriter;

  ApiAccessDeniedHandler(SecurityResponseWriter responseWriter) {
    this.responseWriter = responseWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    responseWriter.ecrireErreur(response, HttpStatus.FORBIDDEN, "Accès refusé");
  }
}
