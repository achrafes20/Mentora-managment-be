package ma.hbdev.rh.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration OpenAPI / Swagger.
 *
 * <p>Définit le schéma de sécurité Bearer JWT afin que Swagger UI permette d'envoyer le token dans
 * les requêtes protégées. Le contrat est exporté via {@code make openapi-export} et écrit dans
 * contracts/openapi.json — source de vérité du contrat API consommé par T0.B1.
 */
@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME_NAME = "BearerAuth";

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Mentora RH — API Backend")
                .description(
                    "API REST Spring Boot — Gestion RH HB Développement. "
                        + "Contrat exporté dans contracts/openapi.json.")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("HB Développement").email("dev@hbdev.ma")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .name(SECURITY_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
