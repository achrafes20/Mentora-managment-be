package ma.hbdev.rh.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.config.ConfigurationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des comptes utilisateurs — réservé aux ADMIN (T1.A1).
 *
 * <p>Implémente :
 *
 * <ul>
 *   <li>EF-AUTH-16 : Création de comptes admin/manager
 *   <li>EF-AUTH-17 : Désactivation de compte
 *   <li>EF-AUTH-18 : Lecture de la liste et du détail
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final SessionRepository sessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final ConfigurationService configurationService;

  /** Retourne tous les utilisateurs (admin uniquement). */
  @Transactional(readOnly = true)
  public List<UserResponse> findAll() {
    return userRepository.findAll().stream().map(UserResponse::fromUser).toList();
  }

  /** Retourne un utilisateur par son id. */
  @Transactional(readOnly = true)
  public UserResponse findById(UUID id) {
    return userRepository
        .findById(id)
        .map(UserResponse::fromUser)
        .orElseThrow(() -> new UserNotFoundException(id));
  }

  /** EF-AUTH-16 : Création d'un compte admin ou manager. */
  @Transactional
  public UserResponse create(UserCreateRequest request) {
    if (userRepository.findByEmail(request.email()).isPresent()) {
      throw new IllegalArgumentException("Un compte avec cet e-mail existe déjà.");
    }

    if (!configurationService.validatePasswordStrength(request.motDePasse())) {
      throw new IllegalArgumentException(
          "Le mot de passe ne respecte pas la politique de sécurité "
              + "(min. 10 caractères, majuscule, minuscule, chiffre).");
    }

    User user = new User();
    user.setEmail(request.email());
    user.setMotDePasseHash(passwordEncoder.encode(request.motDePasse()));
    user.setRole(request.role());
    user.setNom(request.nom());
    user.setPrenom(request.prenom());
    user.setStatut(StatutActifInactif.actif);
    user.setCreeLe(Instant.now());
    user.setModifieLe(Instant.now());

    User saved = userRepository.save(user);
    log.info("Compte créé : {} ({})", saved.getEmail(), saved.getRole());
    return UserResponse.fromUser(saved);
  }

  /** Mise à jour du rôle, nom, prénom. */
  @Transactional
  public UserResponse update(UUID id, UserUpdateRequest request) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

    user.setRole(request.role());
    user.setNom(request.nom());
    user.setPrenom(request.prenom());
    user.setModifieLe(Instant.now());

    User saved = userRepository.save(user);
    log.info("Compte mis à jour : {}", saved.getEmail());
    return UserResponse.fromUser(saved);
  }

  /**
   * EF-AUTH-17 : Désactivation d'un compte (soft-delete). Révoque toutes les sessions actives de
   * l'utilisateur.
   */
  @Transactional
  public UserResponse deactivate(UUID id) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

    user.setStatut(StatutActifInactif.inactif);
    user.setModifieLe(Instant.now());
    userRepository.save(user);

    // Révocation de toutes les sessions actives
    sessionRepository
        .findByUserAndRevoqueLeIsNull(user)
        .forEach(
            session -> {
              session.setRevoqueLe(Instant.now());
              sessionRepository.save(session);
            });

    log.info("Compte désactivé : {}", user.getEmail());
    return UserResponse.fromUser(user);
  }

  /** Réactivation d'un compte inactif. */
  @Transactional
  public UserResponse activate(UUID id) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

    user.setStatut(StatutActifInactif.actif);
    user.setVerrouilleJusquA(null);
    user.setTentativesEchoueesConsecutives(0);
    user.setModifieLe(Instant.now());
    userRepository.save(user);

    log.info("Compte réactivé : {}", user.getEmail());
    return UserResponse.fromUser(user);
  }
}
