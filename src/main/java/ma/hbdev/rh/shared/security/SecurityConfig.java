package ma.hbdev.rh.shared.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration Spring Security.
 *
 * <p>Seuls les endpoints publics (healthcheck, login, reset mot de passe, Swagger) sont ouverts.
 * Tout le reste exige l'authentification (401 si absent) ; le RBAC fin par rôle est appliqué soit
 * ici par {@code requestMatchers}, soit par {@code @PreAuthorize} sur les contrôleurs (ex. {@code
 * UserController}, {@code DepartementController}, {@code EmployeController} — cf. T1.C1).
 *
 * <p>NFR-SEC-01 : stateless (pas de session HTTP), CSRF désactivé pour API REST pure.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/api/auth/login",
    "/api/auth/forgot-password",
    "/api/auth/reset-password",
    // NFR-UX-02 : endpoints appareil du kiosque, sans session JWT — /scan et
    // /activation/statut exigent un jeton d'appareil vérifié en base (KiosqueActivationService),
    // /activation/verifier échange un code contre ce jeton. Volontairement listés un par un plutôt
    // que "/api/kiosque/**" : la gestion des codes (/api/kiosque/activations/**,
    // KiosqueActivationController) exige elle une session JWT Admin/délégué et ne doit pas passer
    // ici.
    "/api/kiosque/scan",
    // EF-ATT-16 : même principe que /scan ci-dessus, pour l'appareil personnel de l'employé —
    // l'identité vient du jeton d'appareil vérifié en base, pas d'une session JWT.
    "/api/kiosque/scan-personnel",
    "/api/kiosque/mes-pointages",
    "/api/kiosque/revoquer-perte",
    "/api/kiosque/activation/statut",
    "/api/kiosque/activation/verifier",
    "/api/fichiers/**",
    // Appelé par n8n (jamais par un utilisateur connecté) — protégé par InternalWebhookGuard
    // (secret partagé en en-tête), pas par une session JWT. EF-REC-02.
    "/api/recruitment/ingest",
    "/api/internal/surveillance/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
  private final ApiAccessDeniedHandler apiAccessDeniedHandler;
  private final List<String> allowedOrigins;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
      ApiAccessDeniedHandler apiAccessDeniedHandler,
      @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
    this.apiAccessDeniedHandler = apiAccessDeniedHandler;
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Sans ça, un refus posé au niveau de la chaîne de filtres (pas d'authentification du
        // tout, ou rôle insuffisant sur une règle requestMatchers ci-dessous) retombe sur le
        // comportement par défaut de Spring Security : 403/401 à corps vide, hors du format
        // ApiResponse. GlobalExceptionHandler ne peut pas le rattraper : il n'intercepte que les
        // exceptions levées à l'intérieur du dispatch Spring MVC (ex. @PreAuthorize).
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(apiAuthenticationEntryPoint)
                    .accessDeniedHandler(apiAccessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                // EndpointRequest (pas un simple requestMatchers(String)) : les endpoints
                // Actuator ne passent pas par le HandlerMapping Spring MVC standard, un
                // matcher par chemin littéral ne les reconnaît pas de façon fiable.
                auth.requestMatchers(EndpointRequest.to("health"))
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    // EF-AUTH-11/12 : seule exception à la règle "/api/users/** = ADMIN"
                    // ci-dessous — UserController#listerManagers() affine lui-même l'accès
                    // via @PreAuthorize ("hasRole('ADMIN') or
                    // @delegationService.estDelegueActif()"),
                    // ce filtre au niveau de la chaîne ne fait que laisser passer un utilisateur
                    // authentifié jusque-là. Doit précéder la règle générale (le premier
                    // requestMatchers qui matche gagne).
                    .requestMatchers("/api/users/managers")
                    .authenticated()
                    .requestMatchers("/api/users/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            jwtAuthenticationFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .build();
  }

  /** Configuration CORS pour autoriser le frontend. */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        Arrays.asList(
            "Authorization",
            "Content-Type",
            "Cache-Control",
            "X-Internal-Webhook-Secret",
            "X-Kiosque-Device-Token"));
    configuration.setExposedHeaders(Arrays.asList("Authorization"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /** BCrypt cost=12 conforme NFR-SEC-02. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
