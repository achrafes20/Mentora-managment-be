package ma.hbdev.rh.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Écrit une {@link ApiResponse#error(String)} directement sur la réponse servlet, pour les points
 * de la chaîne Spring Security (entry point, access denied handler) qui s'exécutent avant le
 * dispatch Spring MVC — {@code GlobalExceptionHandler} (un {@code @RestControllerAdvice}) ne peut
 * pas les intercepter, sinon Spring Security retombe sur son corps vide par défaut.
 */
@Component
class SecurityResponseWriter {

  private final ObjectMapper objectMapper;

  SecurityResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  void ecrireErreur(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
  }
}
