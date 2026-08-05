package ma.hbdev.rh.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Sans ce point d'entrée, une requête sans jeton du tout (jamais passée par {@link
 * JwtAuthenticationFilter}, donc jamais authentifiée) est rejetée par le comportement par défaut de
 * Spring Security : 403 à corps vide, hors du format {@code ApiResponse} attendu par le frontend.
 * Même message que {@code GlobalExceptionHandler#handleAuthentication}, pour un texte cohérent que
 * la requête ait été rejetée avant ou après le dispatch Spring MVC.
 */
@Component
class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SecurityResponseWriter responseWriter;

  ApiAuthenticationEntryPoint(SecurityResponseWriter responseWriter) {
    this.responseWriter = responseWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    responseWriter.ecrireErreur(response, HttpStatus.UNAUTHORIZED, "Authentification requise");
  }
}
