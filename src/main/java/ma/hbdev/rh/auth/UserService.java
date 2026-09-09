package ma.hbdev.rh.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.employee.EmployeService;
import ma.hbdev.rh.shared.security.PasswordPolicy;
import org.springframework.context.ApplicationEventPublisher;
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
  private final PasswordPolicy passwordPolicy;
  private final ApplicationEventPublisher evenements;
  private final EmployeService employeService;

  /** Retourne tous les utilisateurs (admin uniquement). */
  @Transactional(readOnly = true)
  public List<UserResponse> findAll() {
    return userRepository.findAll().stream().map(UserResponse::fromUser).toList();
  }

  /**
   * Adresses e-mail des comptes Admin actifs — destinataires des alertes internes envoyées par
   * l'application elle-même (surveillance des fins de contrat, EF-DOC-12/13/14).
   *
   * <p>Lecture en base plutôt qu'une adresse en configuration : le compte Admin est déjà la source
   * de vérité, et une variable d'environnement dupliquant son e-mail finirait par diverger dès que
   * l'Admin change d'adresse depuis l'application.
   *
   * <p>Renvoie une liste (et non une adresse unique) pour rester correct si un second compte Admin
   * est créé un jour, sans que l'appelant ait à changer.
   */
  @Transactional(readOnly = true)
  public List<String> emailsAdminsActifs() {
    return userRepository
        .findByRoleAndStatut(RoleUtilisateur.admin, StatutActifInactif.actif)
        .stream()
        .map(User::getEmail)
        .toList();
  }

  /**
   * EF-AUTH-11/12 : comptes Manager actifs uniquement — jamais les comptes Admin ni les champs de
   * {@link #findAll()} non nécessaires à un sélecteur (picker manager pour transfert employé,
   * assignation d'entretien...). Volontairement plus étroit que {@link #findAll()} : accessible à
   * un délégué actif (cf. {@code UserController}), qui ne doit jamais hériter d'une visibilité
   * complète sur la gestion des comptes (EF-AUTH-12).
   */
  @Transactional(readOnly = true)
  public List<UserResponse> listerManagersActifs() {
    return userRepository
        .findByRoleAndStatut(RoleUtilisateur.manager, StatutActifInactif.actif)
        .stream()
        .map(UserResponse::fromUser)
        .toList();
  }

  /** Retourne un utilisateur par son id. */
  @Transactional(readOnly = true)
  public UserResponse findById(UUID id) {
    return userRepository
        .findById(id)
        .map(UserResponse::fromUser)
        .orElseThrow(() -> new UserNotFoundException(id));
  }

  /**
   * EF-AUTH-16 : Création d'un compte admin ou manager.
   *
   * <p>EF-EMP-18 : un Manager est aussi un employé — sa fiche RH (département/poste/type de
   * contrat/date d'embauche) est créée dans la même transaction que son compte, et liée via {@code
   * employes.utilisateur_id}. Un Admin n'a pas besoin de fiche RH, ces champs sont ignorés pour ce
   * rôle.
   */
  @Transactional
  public UserResponse create(UserCreateRequest request) {
    if (userRepository.findByEmail(request.email()).isPresent()) {
      throw new IllegalArgumentException("Un compte avec cet e-mail existe déjà.");
    }

    if (!passwordPolicy.valide(request.motDePasse())) {
      throw new IllegalArgumentException(
          "Le mot de passe ne respecte pas la politique de sécurité "
              + "(min. 10 caractères, majuscule, minuscule, chiffre).");
    }

    if (request.role() == RoleUtilisateur.manager) {
      validerFicheRhManager(request);
    }

    User user = new User();
    user.setEmail(request.email());
    user.setMotDePasseHash(passwordEncoder.encode(request.motDePasse()));
    user.setRole(request.role());
    user.setNom(request.nom());
    user.setPrenom(request.prenom());
    user.setMattermostUserId(normaliserMattermostUserId(request.mattermostUserId()));
    user.setStatut(StatutActifInactif.actif);
    user.setCreeLe(Instant.now());
    user.setModifieLe(Instant.now());

    User saved = userRepository.save(user);

    if (request.role() == RoleUtilisateur.manager) {
      employeService.creerPourUtilisateur(
          request.nom(),
          request.prenom(),
          request.email(),
          request.departementId(),
          request.poste(),
          request.typeContrat(),
          request.dateEmbauche(),
          saved.getId());
    }

    evenements.publishEvent(CompteEvent.creation(saved.getId(), saved.getEmail()));
    log.info("Compte créé : {} ({})", saved.getEmail(), saved.getRole());
    return UserResponse.fromUser(saved);
  }

  private void validerFicheRhManager(UserCreateRequest request) {
    if (request.departementId() == null) {
      throw new IllegalArgumentException("Le département est obligatoire pour un Manager.");
    }
    if (request.poste() == null || request.poste().isBlank()) {
      throw new IllegalArgumentException("Le poste est obligatoire pour un Manager.");
    }
    if (request.typeContrat() == null || request.typeContrat().isBlank()) {
      throw new IllegalArgumentException("Le type de contrat est obligatoire pour un Manager.");
    }
    if (request.dateEmbauche() == null) {
      throw new IllegalArgumentException("La date d'embauche est obligatoire pour un Manager.");
    }
  }

  /** Mise à jour du rôle, nom, prénom. */
  @Transactional
  public UserResponse update(UUID id, UserUpdateRequest request) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

    user.setRole(request.role());
    user.setNom(request.nom());
    user.setPrenom(request.prenom());
    user.setMattermostUserId(normaliserMattermostUserId(request.mattermostUserId()));
    user.setModifieLe(Instant.now());

    User saved = userRepository.save(user);
    evenements.publishEvent(CompteEvent.modification(saved.getId(), saved.getEmail()));
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

    evenements.publishEvent(CompteEvent.desactivation(user.getId(), user.getEmail()));
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

    evenements.publishEvent(CompteEvent.activation(user.getId(), user.getEmail()));
    log.info("Compte réactivé : {}", user.getEmail());
    return UserResponse.fromUser(user);
  }

  private String normaliserMattermostUserId(String mattermostUserId) {
    if (mattermostUserId == null || mattermostUserId.isBlank()) {
      return null;
    }
    return mattermostUserId.trim();
  }
}
