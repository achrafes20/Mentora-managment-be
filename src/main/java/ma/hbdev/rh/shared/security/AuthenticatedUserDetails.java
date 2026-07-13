package ma.hbdev.rh.shared.security;

import java.util.UUID;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Détails d'authentification portés par le token — enrichit les {@link WebAuthenticationDetails}
 * standard (IP, session HTTP) avec l'id de l'utilisateur résolu depuis la session applicative (cf.
 * {@link JwtAuthenticationFilter}), pour que n'importe quel module puisse retrouver l'utilisateur
 * courant via {@link CurrentUser} sans requêter le module auth directement.
 */
record AuthenticatedUserDetails(UUID userId, WebAuthenticationDetails webDetails) {}
