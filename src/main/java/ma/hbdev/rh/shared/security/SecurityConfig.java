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
    "/api/kiosque/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final List<String> allowedOrigins;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // EndpointRequest (pas un simple requestMatchers(String)) : les endpoints
                // Actuator ne passent pas par le HandlerMapping Spring MVC standard, un
                // matcher par chemin littéral ne les reconnaît pas de façon fiable.
                auth.requestMatchers(EndpointRequest.to("health"))
                    .permitAll()
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
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
        Arrays.asList("Authorization", "Content-Type", "Cache-Control"));
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
