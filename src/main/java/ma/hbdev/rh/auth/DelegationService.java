package ma.hbdev.rh.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EF-AUTH-11→15 : délégation temporaire des droits d'approbation d'un Admin vers un délégué (autre
 * Admin ou Manager élevé).
 *
 * <p>Public — c'est le point de contact cross-module : {@link #estDelegueActif()} est référencé
 * directement depuis les {@code @PreAuthorize} d'autres modules (ex. recruitment) pour autoriser un
 * délégué actif là où seul un Admin pouvait agir jusqu'ici.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

  private final DelegationRepository delegationRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher evenements;

  /** EF-AUTH-11 : désignation d'un délégué temporaire par l'Admin courant. */
  @Transactional
  public DelegationReponse creer(DelegationCreationRequete requete) {
    UUID adminDelegantId =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));

    if (requete.delegueId().equals(adminDelegantId)) {
      throw new IllegalArgumentException("Impossible de se déléguer des droits à soi-même.");
    }
    if (requete.dateFin().isBefore(requete.dateDebut())) {
      throw new IllegalArgumentException(
          "La date de fin doit être postérieure à la date de début.");
    }

    User delegue =
        userRepository
            .findById(requete.delegueId())
            .orElseThrow(() -> new UserNotFoundException(requete.delegueId()));
    if (!delegue.isActive()) {
      throw new IllegalArgumentException("Le compte désigné comme délégué est désactivé.");
    }

    DelegationApprobation delegation = new DelegationApprobation();
    delegation.setAdminDelegantId(adminDelegantId);
    delegation.setDelegueId(requete.delegueId());
    delegation.setDateDebut(requete.dateDebut());
    delegation.setDateFin(requete.dateFin());
    delegation.setStatut(StatutDelegation.active);

    DelegationApprobation saved = delegationRepository.save(delegation);
    log.info("Délégation créée : {} -> {}", adminDelegantId, requete.delegueId());
    evenements.publishEvent(evenementPeriode(saved, DelegationPeriodeEvent.Phase.debut));
    return DelegationReponse.depuis(saved);
  }

  /**
   * EF-AUTH-13 : révocation manuelle, à tout moment avant l'échéance, par un Admin — y compris
   * avant que la période n'ait commencé (dateDebut future). {@link
   * DelegationApprobation#estEffectivementActive()} exigerait en plus que la période soit en cours,
   * ce qui bloquerait à tort l'annulation d'une délégation seulement planifiée ; {@link
   * DelegationApprobation#statutEffectif()} ne rejette que le déjà-révoqué/déjà-expiré.
   */
  @Transactional
  public DelegationReponse revoquer(UUID id) {
    DelegationApprobation delegation =
        delegationRepository.findById(id).orElseThrow(() -> new DelegationNotFoundException(id));

    if (delegation.statutEffectif() != StatutDelegation.active) {
      throw new IllegalArgumentException("Cette délégation n'est plus active.");
    }

    delegation.setStatut(StatutDelegation.revoquee);
    delegation.setRevoqueParId(CurrentUser.id().orElse(null));
    delegation.setRevoqueLe(Instant.now());

    DelegationApprobation saved = delegationRepository.save(delegation);
    log.info("Délégation révoquée : {}", id);
    evenements.publishEvent(evenementPeriode(saved, DelegationPeriodeEvent.Phase.fin));
    return DelegationReponse.depuis(saved);
  }

  /**
   * EF-AUTH-13/15 : purge quotidienne des délégations dont l'échéance est dépassée sans révocation
   * manuelle — bascule le statut persisté vers {@code expiree} (jusqu'ici calculé à la lecture
   * seulement, cf. {@link DelegationApprobation#statutEffectif()}) et déclenche le ping Mattermost
   * de fin, sinon jamais émis pour une expiration naturelle.
   */
  @Scheduled(cron = "${app.delegations.expiration-cron:0 30 2 * * *}")
  @Transactional
  public void expirerDelegationsEcheues() {
    List<DelegationApprobation> echues =
        delegationRepository.findByStatutAndDateFinBefore(StatutDelegation.active, LocalDate.now());
    for (DelegationApprobation delegation : echues) {
      delegation.setStatut(StatutDelegation.expiree);
      DelegationApprobation saved = delegationRepository.save(delegation);
      log.info("Délégation expirée automatiquement : {}", saved.getId());
      evenements.publishEvent(evenementPeriode(saved, DelegationPeriodeEvent.Phase.fin));
    }
  }

  private DelegationPeriodeEvent evenementPeriode(
      DelegationApprobation delegation, DelegationPeriodeEvent.Phase phase) {
    return new DelegationPeriodeEvent(
        delegation.getId(),
        phase,
        delegation.getAdminDelegantId(),
        delegation.getDelegueId(),
        delegation.getDateDebut(),
        delegation.getDateFin());
  }

  /** Historique complet, le plus récent en premier — statut présenté avec expiration calculée. */
  @Transactional(readOnly = true)
  public List<DelegationReponse> lister() {
    return delegationRepository.findAllByOrderByCreeLeDesc().stream()
        .map(DelegationReponse::depuis)
        .toList();
  }

  /**
   * EF-AUTH-11/12 : vrai si l'utilisateur courant est actuellement un délégué actif — droits
   * d'approbation/rejet des demandes administratives et de décision de recrutement. Ne couvre
   * jamais la gestion des comptes ni la configuration (EF-AUTH-12) : ce booléen n'est référencé que
   * par les endpoints où ce cumul est explicitement voulu.
   */
  @Transactional(readOnly = true)
  public boolean estDelegueActif() {
    return delegationActiveId().isPresent();
  }

  /**
   * EF-AUTH-14 : id de la délégation sous laquelle l'utilisateur courant agit actuellement, s'il
   * est un délégué actif — consommé par l'écouteur d'audit pour marquer les décisions prises en
   * délégation.
   */
  @Transactional(readOnly = true)
  public Optional<UUID> delegationActiveId() {
    return delegationActiveEffectivePourUtilisateurCourant().map(DelegationApprobation::getId);
  }

  /**
   * Délégation sous laquelle l'utilisateur courant agit actuellement en tant que délégué, si active
   * — c'est ce que consomme {@code GET /api/delegations/moi}, seul moyen pour un Manager (jamais
   * autorisé sur {@link #lister()}, réservé Admin) de savoir s'il doit voir les actions
   * d'approbation/décision côté frontend.
   */
  @Transactional(readOnly = true)
  public Optional<DelegationReponse> delegationActivePourUtilisateurCourant() {
    return delegationActiveEffectivePourUtilisateurCourant().map(DelegationReponse::depuis);
  }

  /**
   * Vrai si la délégation désignée par {@code delegationId} est encore effectivement active — même
   * filtre que {@link #delegationActiveEffectivePourUtilisateurCourant()} ci-dessous, mais pour une
   * délégation connue par id plutôt que "celle de l'utilisateur courant". Consommé par
   * KiosqueActivationService (NFR-UX-02) : un code d'activation kiosque émis par un délégué reste
   * valide seulement pendant la fenêtre de sa délégation, sans mécanisme d'expiration séparé.
   */
  @Transactional(readOnly = true)
  public boolean estActive(UUID delegationId) {
    return delegationRepository
        .findById(delegationId)
        .map(DelegationApprobation::estEffectivementActive)
        .orElse(false);
  }

  private Optional<DelegationApprobation> delegationActiveEffectivePourUtilisateurCourant() {
    UUID courant = CurrentUser.id().orElse(null);
    if (courant == null) {
      return Optional.empty();
    }
    return delegationRepository
        .findFirstByDelegueIdAndStatut(courant, StatutDelegation.active)
        .filter(DelegationApprobation::estEffectivementActive);
  }
}
