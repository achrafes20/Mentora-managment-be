package ma.hbdev.rh.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.auth.Session;
import ma.hbdev.rh.auth.SessionRepository;
import ma.hbdev.rh.shared.config.ConfigurationService;
import ma.hbdev.rh.shared.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final SessionRepository sessionRepository;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(7);
    try {
      if (jwtService.isTokenExpired(token)) {
        writeErrorResponse(response, "Session expirée", HttpStatus.UNAUTHORIZED);
        return;
      }

      String email = jwtService.extractEmail(token);
      String role = jwtService.extractRole(token);
      String tokenHash = jwtService.hashToken(token);

      Optional<Session> sessionOpt = sessionRepository.findByJetonHash(tokenHash);
      if (sessionOpt.isEmpty()) {
        writeErrorResponse(response, "Authentification requise", HttpStatus.UNAUTHORIZED);
        return;
      }

      Session session = sessionOpt.get();
      if (!session.isValid()) {
        writeErrorResponse(response, "Session invalide ou révoquée", HttpStatus.UNAUTHORIZED);
        return;
      }

      // Validation de l'inactivité (EF-AUTH-10)
      int inactivityLimitMinutes = configurationService.getDureeSessionInactiviteMinutes();
      Instant lastActivity = session.getDerniereActiviteLe();
      if (Duration.between(lastActivity, Instant.now()).toMinutes() >= inactivityLimitMinutes) {
        session.setRevoqueLe(Instant.now());
        sessionRepository.save(session);
        writeErrorResponse(response, "Session expirée pour inactivité", HttpStatus.UNAUTHORIZED);
        return;
      }

      // Mise à jour de l'activité
      session.setDerniereActiviteLe(Instant.now());
      sessionRepository.save(session);

      // Enregistrement du contexte de sécurité
      UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(
              email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
      authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authToken);

    } catch (Exception e) {
      log.error("Erreur lors de la validation du jeton d'authentification", e);
      writeErrorResponse(response, "Authentification échouée", HttpStatus.UNAUTHORIZED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void writeErrorResponse(HttpServletResponse response, String message, HttpStatus status)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ApiResponse<Void> apiResponse = ApiResponse.error(message);
    response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
  }
}
