package ma.hbdev.rh.attendance;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.auth.DelegationService;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NFR-UX-02 : jeton d'activation par appareil pour le kiosque de pointage — remplace le {@code
 * permitAll()} inconditionnel de {@code /api/kiosque/scan}.
 *
 * <p>Verrouillage des tentatives ({@code tentativesEchoueesConsecutives}/{@code verrouilleJusquA})
 * : même algorithme et même configuration ({@code app.security.lockout.*}) que le verrouillage de
 * compte (EF-AUTH-02→04, {@code AuthService}), appliqué ici au code {@code en_attente} le plus
 * récent. Différence avec un login : un code d'activation est un secret opaque auto-porteur —
 * contrairement à un couple email/mot de passe, il n'y a pas d'identifiant séparé pour retrouver la
 * ligne visée avant de vérifier le code, donc on cible la dernière ligne émise en l'absence de
 * meilleure piste.
 */
@Service
@Transactional
class KiosqueActivationService {

  // Exclut 0/O/1/I : code relayé oralement ou tapé par la personne qui installe le kiosque.
  private static final String ALPHABET_CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int LONGUEUR_CODE = 6;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KiosqueActivationRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DelegationService delegationService;
  private final ApplicationEventPublisher evenements;

  @Value("${app.security.lockout.max-attempts:5}")
  private int tentativesMax;

  @Value("${app.security.lockout.delay-minutes:30}")
  private int delaiDeverrouillageMinutes;

  // NFR-UX-02 : un code généré mais jamais saisi (statut en_attente) ne doit pas rester valide
  // indéfiniment — 24h laisse le temps de le transmettre physiquement à la personne qui installe
  // le kiosque, sans traîner en base pour toujours comme avant.
  @Value("${app.security.kiosque.expiration-code-heures:24}")
  private int expirationCodeHeures;

  KiosqueActivationService(
      KiosqueActivationRepository repository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      DelegationService delegationService,
      ApplicationEventPublisher evenements) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.delegationService = delegationService;
    this.evenements = evenements;
  }

  record CodeGenere(UUID id, String code) {}

  /** Génère un nouveau code d'activation (Admin, ou délégué actif — EF-AUTH-11/12). */
  CodeGenere genererCode() {
    UUID emisPar =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    UUID delegationId = delegationService.delegationActiveId().orElse(null);

    String code = genererCodeAleatoire();
    KiosqueActivation saved =
        repository.save(new KiosqueActivation(passwordEncoder.encode(code), emisPar, delegationId));

    evenements.publishEvent(
        KiosqueActivationEvent.generation(saved.getId(), emisPar, delegationId));
    return new CodeGenere(saved.getId(), code);
  }

  /**
   * EF-ATT-02/NFR-UX-02 : vérifie un code saisi côté kiosque et active l'appareil si valide.
   * Retourne le jeton d'appareil en clair (à stocker côté client) — jamais persisté en clair.
   *
   * <p>{@code noRollbackFor} : même motif que {@code AuthService#login} — sans ça, l'incrément du
   * compteur d'échecs serait annulé par le rollback déclenché par l'exception qu'on lève juste
   * après pour signaler l'échec à l'appelant (RuntimeException => rollback par défaut).
   */
  @Transactional(noRollbackFor = CodeActivationInvalideException.class)
  String verifierCode(String codeSaisi) {
    String normalise = codeSaisi == null ? "" : codeSaisi.trim().toUpperCase();
    List<KiosqueActivation> enAttenteBrut =
        repository.findByStatutOrderByEmisLeDesc(StatutActivationKiosque.en_attente);
    Instant maintenant = Instant.now();
    List<KiosqueActivation> enAttente =
        enAttenteBrut.stream().filter(a -> !estExpiree(a, maintenant)).toList();
    List<KiosqueActivation> expirees =
        enAttenteBrut.stream().filter(a -> estExpiree(a, maintenant)).toList();

    for (KiosqueActivation candidat : enAttente) {
      if (!candidat.isVerrouillee() && passwordEncoder.matches(normalise, candidat.getCodeHash())) {
        String jetonAppareil = genererJetonAppareil();
        candidat.activer(jwtService.hashToken(jetonAppareil));
        repository.save(candidat);
        evenements.publishEvent(
            KiosqueActivationEvent.activation(
                candidat.getId(), candidat.getEmisPar(), candidat.getDelegationId()));
        return jetonAppareil;
      }
    }

    // Aucun code en attente ne correspond : peut-être un code réellement valide mais déjà consommé
    // (activé ou révoqué) qu'on ressaisit par erreur/habitude — ce n'est pas une tentative de
    // devinette, ne jamais pénaliser ce cas, message distinct pour ne pas laisser croire à une
    // faute de frappe.
    for (KiosqueActivation traite :
        repository.findByStatutIn(
            List.of(StatutActivationKiosque.active, StatutActivationKiosque.revoquee))) {
      if (passwordEncoder.matches(normalise, traite.getCodeHash())) {
        throw new CodeActivationDejaUtiliseException();
      }
    }

    // Idem pour un code jamais consommé mais expiré (24h par défaut) : message distinct, pas de
    // pénalité — ce n'est pas une tentative de devinette, juste un code périmé.
    for (KiosqueActivation expiree : expirees) {
      if (passwordEncoder.matches(normalise, expiree.getCodeHash())) {
        throw new CodeActivationExpireException();
      }
    }

    // Aucun code ne correspond nulle part : pénalise le plus récent en attente (cf. javadoc de la
    // classe).
    if (!enAttente.isEmpty()) {
      KiosqueActivation dernier = enAttente.get(0);
      if (dernier.isVerrouillee()) {
        throw new CodeActivationInvalideException(
            "Code temporairement verrouillé.", dernier.getVerrouilleJusquA());
      }
      dernier.enregistrerEchec(tentativesMax, delaiDeverrouillageMinutes);
      repository.save(dernier);
      if (dernier.isVerrouillee()) {
        throw new CodeActivationInvalideException(
            "Code temporairement verrouillé.", dernier.getVerrouilleJusquA());
      }
    }
    throw new CodeActivationInvalideException("Code d'activation invalide.");
  }

  private boolean estExpiree(KiosqueActivation activation, Instant maintenant) {
    return activation.getEmisLe().plus(Duration.ofHours(expirationCodeHeures)).isBefore(maintenant);
  }

  /** Vrai si le jeton d'appareil présenté correspond à une activation active et toujours valide. */
  @Transactional(readOnly = true)
  boolean estAppareilActif(String jetonAppareil) {
    if (jetonAppareil == null || jetonAppareil.isBlank()) {
      return false;
    }
    String hash = jwtService.hashToken(jetonAppareil);
    return repository
        .findByDeviceTokenHashAndStatut(hash, StatutActivationKiosque.active)
        .filter(this::estEncoreValide)
        .isPresent();
  }

  /** Lève si le jeton d'appareil ne correspond à aucune activation active et valide. */
  @Transactional(readOnly = true)
  void verifierAppareilActif(String jetonAppareil) {
    if (!estAppareilActif(jetonAppareil)) {
      throw new AppareilNonActiveException();
    }
  }

  // EF-AUTH-11/12 : un code émis par un délégué reste valide seulement pendant la fenêtre de sa
  // délégation — pas de mécanisme d'expiration séparé, même filtre en lecture que
  // DelegationService#delegationActiveEffectivePourUtilisateurCourant() (~ligne 172), appliqué ici
  // à une délégation connue par id plutôt qu'à celle de l'utilisateur courant.
  private boolean estEncoreValide(KiosqueActivation activation) {
    UUID delegationId = activation.getDelegationId();
    return delegationId == null || delegationService.estActive(delegationId);
  }

  /**
   * Écran de gestion (Admin, ou délégué actif) — jamais le code ni le jeton d'appareil en clair.
   */
  @Transactional(readOnly = true)
  List<KiosqueActivation> lister() {
    return repository.findAllByOrderByEmisLeDesc();
  }

  /**
   * Désactivation manuelle (Admin, ou délégué actif) — pas d'expiration automatique (provisoire).
   */
  void revoquer(UUID id) {
    KiosqueActivation activation =
        repository.findById(id).orElseThrow(() -> new KiosqueActivationIntrouvableException(id));
    UUID revoqueePar =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    activation.revoquer(revoqueePar);
    repository.save(activation);
    evenements.publishEvent(KiosqueActivationEvent.revocation(activation.getId(), revoqueePar));
  }

  private static String genererCodeAleatoire() {
    StringBuilder sb = new StringBuilder(LONGUEUR_CODE);
    for (int i = 0; i < LONGUEUR_CODE; i++) {
      sb.append(ALPHABET_CODE.charAt(RANDOM.nextInt(ALPHABET_CODE.length())));
    }
    return sb.toString();
  }

  private static String genererJetonAppareil() {
    return UUID.randomUUID().toString().replace("-", "")
        + UUID.randomUUID().toString().replace("-", "");
  }
}
