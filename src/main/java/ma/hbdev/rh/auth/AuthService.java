package ma.hbdev.rh.auth;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.config.ConfigurationService;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service métier d'authentification (T1.A1).
 *
 * <p>Implémente :
 *
 * <ul>
 *   <li>EF-AUTH-01 : Login JWT (email + mot de passe)
 *   <li>EF-AUTH-02 à 04 : Gestion du verrouillage (5 tentatives, délai configurable)
 *   <li>EF-AUTH-05 : Logout (révocation de la session)
 *   <li>EF-AUTH-06 à 08 : Réinitialisation de mot de passe (via n8n/Mailpit)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserRepository userRepository;
  private final SessionRepository sessionRepository;
  private final PasswordResetRepository passwordResetRepository;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final ConfigurationService configurationService;
  private final MailService mailService;

  /**
   * EF-AUTH-01 : Authentifie un utilisateur et retourne un JWT. EF-AUTH-02/03/04 : Gère le
   * verrouillage après tentatives échouées.
   */
  @Transactional(noRollbackFor = AuthException.class)
  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new AuthException("Identifiants invalides"));

    // Vérification du statut du compte
    if (!user.isActive()) {
      throw new AuthException("Compte désactivé. Contactez l'administrateur.");
    }

    // Vérification du verrouillage (EF-AUTH-03)
    if (user.isLocked()) {
      throw new AuthException("Compte temporairement verrouillé. Réessayez dans quelques minutes.");
    }

    // Vérification du mot de passe
    if (!passwordEncoder.matches(request.motDePasse(), user.getMotDePasseHash())) {
      handleFailedAttempt(user);
      throw new AuthException("Identifiants invalides");
    }

    // Réinitialisation des tentatives après succès
    user.setTentativesEchoueesConsecutives(0);
    user.setVerrouilleJusquA(null);
    user.setDerniereConnexionLe(Instant.now());
    user.setModifieLe(Instant.now());

    // Génération du JWT
    String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
    long expirationMs = 86_400_000L; // 24h par défaut

    // Création de la session en base (EF-AUTH-09)
    Session session = new Session();
    session.setUser(user);
    session.setJetonHash(jwtService.hashToken(token));
    session.setExpireLe(Instant.now().plusMillis(expirationMs));
    session.setDerniereActiviteLe(Instant.now());
    sessionRepository.save(session);

    userRepository.save(user);

    log.info("Connexion réussie pour l'utilisateur : {}", user.getEmail());
    return new LoginResponse(token, UserResponse.fromUser(user));
  }

  /** EF-AUTH-05 : Révoque la session correspondant au jeton fourni. */
  @Transactional
  public void logout(String tokenHash) {
    sessionRepository
        .findByJetonHash(tokenHash)
        .ifPresent(
            session -> {
              session.setRevoqueLe(Instant.now());
              sessionRepository.save(session);
              log.info("Session révoquée pour l'utilisateur : {}", session.getUser().getEmail());
            });
  }

  /**
   * EF-AUTH-06 : Demande de réinitialisation de mot de passe. Envoie un e-mail via le webhook n8n
   * (Mailpit visible en DEV).
   */
  @Transactional
  public void forgotPassword(String email) {
    // Pas de révélation si l'e-mail existe ou non (sécurité)
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              // Génération d'un token sécurisé
              String rawToken =
                  UUID.randomUUID().toString().replace("-", "")
                      + UUID.randomUUID().toString().replace("-", "");
              String tokenHash = jwtService.hashToken(rawToken);

              // Invalidation des anciens tokens non utilisés
              passwordResetRepository
                  .findByTokenHash(tokenHash)
                  .ifPresent(
                      old -> {
                        old.setUtilise(true);
                        passwordResetRepository.save(old);
                      });

              PasswordReset reset = new PasswordReset();
              reset.setUser(user);
              reset.setTokenHash(tokenHash);
              reset.setExpireLe(Instant.now().plusSeconds(3600)); // 1 heure
              passwordResetRepository.save(reset);

              String resetLink = "http://localhost:5173/reset-password?token=" + rawToken;
              String subject = "[Mentora] Réinitialisation de votre mot de passe";
              String message =
                  "Bonjour "
                      + user.getPrenom()
                      + ",\n\n"
                      + "Vous avez demandé une réinitialisation de votre mot de passe.\n"
                      + "Cliquez sur le lien suivant (valable 1 heure) :\n\n"
                      + resetLink
                      + "\n\n"
                      + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.\n\n"
                      + "L'équipe Mentora";

              mailService.sendEmail(user.getEmail(), subject, message);
              log.info("Lien de réinitialisation envoyé à : {}", email);
            });
  }

  /** EF-AUTH-07/08 : Réinitialisation effective du mot de passe avec le token. */
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String tokenHash = jwtService.hashToken(request.token());

    PasswordReset reset =
        passwordResetRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthException("Lien de réinitialisation invalide ou expiré."));

    if (!reset.isValid()) {
      throw new AuthException("Lien de réinitialisation invalide ou expiré.");
    }

    User user = reset.getUser();

    // Validation de la politique de mot de passe
    if (!configurationService.validatePasswordStrength(request.nouveauMotDePasse())) {
      throw new AuthException(
          "Le nouveau mot de passe ne respecte pas la politique de sécurité "
              + "(min. 10 caractères, majuscule, minuscule, chiffre).");
    }

    user.setMotDePasseHash(passwordEncoder.encode(request.nouveauMotDePasse()));
    user.setTentativesEchoueesConsecutives(0);
    user.setVerrouilleJusquA(null);
    user.setModifieLe(Instant.now());
    userRepository.save(user);

    // Invalidation du token de réinitialisation
    reset.setUtilise(true);
    passwordResetRepository.save(reset);

    // Révocation de toutes les sessions actives (sécurité)
    sessionRepository
        .findByUserAndRevoqueLeIsNull(user)
        .forEach(
            session -> {
              session.setRevoqueLe(Instant.now());
              sessionRepository.save(session);
            });

    log.info("Mot de passe réinitialisé pour : {}", user.getEmail());
  }

  // ---------- méthodes privées ----------

  private void handleFailedAttempt(User user) {
    int maxAttempts = configurationService.getTentativesMax();
    int delayMinutes = configurationService.getDelaiDeverrouillageMinutes();

    int attempts = user.getTentativesEchoueesConsecutives() + 1;
    user.setTentativesEchoueesConsecutives(attempts);

    if (attempts >= maxAttempts) {
      user.setVerrouilleJusquA(Instant.now().plusSeconds((long) delayMinutes * 60));
      log.warn("Compte verrouillé après {} tentatives : {}", attempts, user.getEmail());
    } else {
      log.warn("Tentative échouée {}/{} pour : {}", attempts, maxAttempts, user.getEmail());
    }
    user.setModifieLe(Instant.now());
    userRepository.save(user);
  }
}
