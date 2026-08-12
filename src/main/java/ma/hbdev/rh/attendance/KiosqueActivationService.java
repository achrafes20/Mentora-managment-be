package ma.hbdev.rh.attendance;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.auth.DelegationService;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.shared.mail.MailService;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

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

  // EF-ATT-18 : 4 chiffres — plus facile à mémoriser/retransmettre pour un employé qu'un code
  // alphanumérique. Le verrouillage après échecs (tentativesMax/délai ci-dessous) reste la
  // protection contre la force brute sur un espace aussi réduit (10 000 combinaisons).
  private static final String ALPHABET_CODE = "0123456789";
  private static final int LONGUEUR_CODE = 4;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KiosqueActivationRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DelegationService delegationService;
  private final ApplicationEventPublisher evenements;
  private final MailService mailService;
  private final JdbcTemplate jdbcTemplate;

  @Value("${app.security.lockout.max-attempts:5}")
  private int tentativesMax;

  @Value("${app.security.lockout.delay-minutes:30}")
  private int delaiDeverrouillageMinutes;

  // NFR-UX-02 : un code généré mais jamais saisi (statut en_attente) ne doit pas rester valide
  // indéfiniment — 24h laisse le temps de le transmettre physiquement à la personne qui installe
  // le kiosque, sans traîner en base pour toujours comme avant.
  @Value("${app.security.kiosque.expiration-code-heures:24}")
  private int expirationCodeHeures;

  // EF-ATT-19 : base des liens envoyés par e-mail (lien de révocation) — même propriété que
  // AuthService pour les liens de réinitialisation de mot de passe.
  @Value("${app.base-url:http://localhost:5173}")
  private String appBaseUrl;

  KiosqueActivationService(
      KiosqueActivationRepository repository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      DelegationService delegationService,
      ApplicationEventPublisher evenements,
      MailService mailService,
      JdbcTemplate jdbcTemplate) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.delegationService = delegationService;
    this.evenements = evenements;
    this.mailService = mailService;
    this.jdbcTemplate = jdbcTemplate;
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
   * EF-ATT-18 : code d'activation lié à un employé précis — une fois saisi sur son téléphone,
   * l'appareil devient l'identité de cet employé (pas un kiosque partagé). Envoyé automatiquement
   * par e-mail (pas seulement affiché à l'Admin comme {@link #genererCode()}) : appelé à la fois à
   * la création d'un employé (cf. {@link #gererEvenementEmploye}) et pour régénérer un code depuis
   * l'écran Présence — un seul point d'envoi, un seul gabarit de message.
   */
  CodeGenere genererCodePersonnel(UUID employeId) {
    UUID emisPar =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    UUID delegationId = delegationService.delegationActiveId().orElse(null);

    String code = genererCodeAleatoire();
    KiosqueActivation saved =
        repository.save(
            new KiosqueActivation(passwordEncoder.encode(code), emisPar, delegationId, employeId));

    String jetonRevocation = genererJetonAppareil();
    saved.definirJetonRevocation(jwtService.hashToken(jetonRevocation));
    repository.save(saved);

    evenements.publishEvent(
        KiosqueActivationEvent.generation(saved.getId(), emisPar, delegationId));
    envoyerEmailCode(employeId, code, jetonRevocation);
    return new CodeGenere(saved.getId(), code);
  }

  // EF-ATT-18 : déclenché à la création d'un employé (EmployeModifieEvent action "creation") —
  // le nouvel employé reçoit directement son code de pointage mobile, pas d'étape manuelle
  // supplémentaire pour l'Admin. CurrentUser reste résolu sur l'Admin qui a créé la fiche (même
  // thread de requête, seulement après le commit — cf. AFTER_COMMIT ci-dessous).
  // @TransactionalEventListener (pas @EventListener) : EmployeService#creer publie cet événement
  // en plein milieu de sa propre transaction, juste après employeRepository.save(...) — pour une
  // entité à ID généré côté client (UUID), Hibernate peut différer l'INSERT réel jusqu'au flush.
  // Un @EventListener classique tournait donc AVANT que la ligne employe soit visible, et
  // employeInfoCode() (lecture SQL brute, hors du cache Hibernate) ne trouvait rien : le code
  // d'activation était bien créé (repository.save ici participe à la même transaction ambiante,
  // peu importe l'ordre), mais l'e-mail ne partait jamais, silencieusement (aucun log, ni succès
  // ni échec, puisque mailService.sendEmail n'était jamais atteint). AFTER_COMMIT (défaut) attend
  // que la transaction d'EmployeService#creer soit réellement validée avant de lire l'employé.
  // Propagation REQUIRES_NEW obligatoire ici : Spring refuse @TransactionalEventListener sur une
  // méthode qui hériterait du @Transactional (REQUIRED) de la classe — à AFTER_COMMIT, la
  // transaction d'origine est déjà terminée, il en faut explicitement une nouvelle.
  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void gererEvenementEmploye(EmployeModifieEvent event) {
    if ("creation".equals(event.action())) {
      genererCodePersonnel(event.employeId());
    } else if ("desactivation".equals(event.action())
        || "desactivation_auto".equals(event.action())) {
      revoquerAppareilsDe(event.employeId());
    }
  }

  // EF-ATT-18 : appareil(s) personnel(s) actif(s) révoqué(s) automatiquement à la désactivation de
  // l'employé — silencieux (pas d'e-mail "contactez les RH", contrairement à revoquer() ci-dessous,
  // puisque l'employé n'a plus besoin de pointer).
  private void revoquerAppareilsDe(UUID employeId) {
    for (KiosqueActivation activation :
        repository.findByStatutIn(List.of(StatutActivationKiosque.active))) {
      if (employeId.equals(activation.getEmployeId())) {
        activation.revoquer(CurrentUser.id().orElse(null));
        repository.save(activation);
        evenements.publishEvent(
            KiosqueActivationEvent.revocation(activation.getId(), activation.getRevoqueePar()));
      }
    }
  }

  private record EmployeInfoCode(
      String email,
      String nomComplet,
      String poste,
      String typeContrat,
      LocalDate dateEmbauche,
      LocalDate dateFinContratPrevue,
      LocalDate dateFinStagePrevue,
      String statut) {}

  private EmployeInfoCode employeInfoCode(UUID employeId) {
    List<EmployeInfoCode> resultats =
        jdbcTemplate.query(
            """
            select email, prenom, nom, poste, type_contrat::text, date_embauche,
                   date_fin_contrat_prevue, date_fin_stage_prevue, statut::text
              from employes where id = ?
            """,
            (rs, rowNum) ->
                new EmployeInfoCode(
                    rs.getString("email"),
                    rs.getString("prenom") + " " + rs.getString("nom"),
                    rs.getString("poste"),
                    rs.getString("type_contrat"),
                    rs.getObject("date_embauche", LocalDate.class),
                    rs.getObject("date_fin_contrat_prevue", LocalDate.class),
                    rs.getObject("date_fin_stage_prevue", LocalDate.class),
                    rs.getString("statut")),
            employeId);
    return resultats.isEmpty() ? null : resultats.get(0);
  }

  private void envoyerEmailCode(UUID employeId, String code, String jetonRevocation) {
    EmployeInfoCode employe = employeInfoCode(employeId);
    if (employe == null || employe.email() == null || employe.email().isBlank()) {
      return;
    }
    String duree =
        switch (employe.typeContrat() == null ? "" : employe.typeContrat()) {
          case "CDD" -> "du " + employe.dateEmbauche() + " au " + employe.dateFinContratPrevue();
          case "STAGIAIRE", "STAGIAIRE_REMUNERE" ->
              "du " + employe.dateEmbauche() + " au " + employe.dateFinStagePrevue();
          default -> "depuis le " + employe.dateEmbauche() + " (durée indéterminée)";
        };
    // EF-ATT-19 : lien à usage unique — permet de révoquer soi-même un téléphone perdu sans
    // repasser par les RH pour ce geste précis (un nouveau code, lui, reste toujours émis par RH).
    String lienRevocation =
        appBaseUrl.replaceAll("/+$", "") + "/pointage-mobile/revoquer?jeton=" + jetonRevocation;
    String message =
        "Bonjour "
            + employe.nomComplet()
            + ",\n\nVoici votre code personnel de pointage mobile.\n\n"
            + "Poste : "
            + (employe.poste() == null ? "" : employe.poste())
            + "\nDurée : "
            + duree
            + "\n\nCode d'activation : "
            + code
            + "\n\nOuvrez /pointage-mobile sur votre téléphone et saisissez ce code pour "
            + "l'activer, puis scannez le QR affiché sur votre lieu de travail pour pointer.\n\n"
            + "Téléphone perdu ou volé ? Révoquez cet accès immédiatement : "
            + lienRevocation
            + "\n(vous pourrez ensuite recontacter les RH pour recevoir un nouveau code)\n\n"
            + "Cordialement, RH";
    mailService.sendEmail(employe.email(), "Votre code de pointage mobile", message);
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

  /**
   * EF-ATT-16 : résout l'employé propriétaire d'un appareil personnel actif. Lève si le jeton est
   * invalide/inactif, ou s'il correspond à un kiosque partagé (employeId null — /scan-personnel
   * n'est pas le bon endpoint pour ce cas, cf. KiosqueController).
   */
  @Transactional(readOnly = true)
  UUID employeAppareilPersonnel(String jetonAppareil) {
    if (jetonAppareil == null || jetonAppareil.isBlank()) {
      throw new AppareilNonActiveException();
    }
    String hash = jwtService.hashToken(jetonAppareil);
    KiosqueActivation activation =
        repository
            .findByDeviceTokenHashAndStatut(hash, StatutActivationKiosque.active)
            .filter(this::estEncoreValide)
            .orElseThrow(AppareilNonActiveException::new);
    if (activation.getEmployeId() == null) {
      throw new AppareilNonActiveException();
    }
    return activation.getEmployeId();
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
   * EF-ATT-18 : pour un appareil personnel dont l'employé est toujours actif, prévient l'employé
   * par e-mail qu'il doit repasser par les RH pour un nouveau code — contrairement à {@link
   * #revoquerAppareilsDe}, jamais déclenché quand l'employé lui-même vient d'être désactivé (il n'a
   * alors plus besoin d'être notifié).
   */
  void revoquer(UUID id) {
    KiosqueActivation activation =
        repository.findById(id).orElseThrow(() -> new KiosqueActivationIntrouvableException(id));
    UUID revoqueePar =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    UUID employeId = activation.getEmployeId();
    activation.revoquer(revoqueePar);
    repository.save(activation);
    evenements.publishEvent(KiosqueActivationEvent.revocation(activation.getId(), revoqueePar));
    if (employeId != null) {
      envoyerEmailRevocation(employeId);
    }
  }

  /**
   * EF-ATT-19 : révocation en self-service via le lien reçu par e-mail — le jeton lui-même est la
   * preuve d'intention (comme un lien de désinscription), pas de session/identité à vérifier. À
   * usage unique : {@link KiosqueActivation#revoquer} efface le jeton, un lien déjà utilisé ne
   * fonctionne donc plus. Pas d'e-mail "contactez les RH" envoyé ici (contrairement à {@link
   * #revoquer(UUID)}) : l'employé vient de faire cette démarche lui-même, il le sait déjà.
   */
  void revoquerParJeton(String jetonBrut) {
    if (jetonBrut == null || jetonBrut.isBlank()) {
      throw new KiosqueActivationIntrouvableException(null);
    }
    String hash = jwtService.hashToken(jetonBrut);
    KiosqueActivation activation =
        repository
            .findByJetonRevocationHash(hash)
            .orElseThrow(() -> new KiosqueActivationIntrouvableException(null));
    activation.revoquer(null);
    repository.save(activation);
    evenements.publishEvent(KiosqueActivationEvent.revocation(activation.getId(), null));
  }

  private void envoyerEmailRevocation(UUID employeId) {
    EmployeInfoCode employe = employeInfoCode(employeId);
    if (employe == null
        || employe.email() == null
        || employe.email().isBlank()
        || "inactif".equals(employe.statut())) {
      return;
    }
    String message =
        "Bonjour "
            + employe.nomComplet()
            + ",\n\nVotre code de pointage mobile a été révoqué. Merci de contacter les "
            + "RH pour en recevoir un nouveau.\n\nCordialement, RH";
    mailService.sendEmail(employe.email(), "Votre accès pointage mobile a été révoqué", message);
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
