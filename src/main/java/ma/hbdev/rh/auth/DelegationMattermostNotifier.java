package ma.hbdev.rh.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hbdev.rh.shared.mattermost.MattermostClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EF-AUTH-15 : prévient l'ensemble des Managers actifs, par Mattermost, au démarrage et à la fin
 * d'une période de délégation active. Même garantie qu'EF-NOTIF-06 : un échec Mattermost pour un
 * Manager (ou pour tous) ne remonte jamais et ne bloque jamais l'action déclenchante.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class DelegationMattermostNotifier {

  private final UserRepository userRepository;
  private final MattermostClient mattermostClient;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void notifier(DelegationPeriodeEvent evenement) {
    User delegue = userRepository.findById(evenement.delegueId()).orElse(null);
    User adminDelegant = userRepository.findById(evenement.adminDelegantId()).orElse(null);
    if (delegue == null || adminDelegant == null) {
      log.warn(
          "Delegation {} : delegue ou admin delegant introuvable, notification annulee",
          evenement.delegationId());
      return;
    }
    String message = formaterMessage(evenement, delegue, adminDelegant);
    userRepository
        .findByRoleAndStatut(RoleUtilisateur.manager, StatutActifInactif.actif)
        .forEach(
            manager -> {
              try {
                mattermostClient.envoyerMessagePrive(manager.getId(), message);
              } catch (RuntimeException exception) {
                log.warn(
                    "Echec notification Mattermost delegation vers manager {}",
                    manager.getId(),
                    exception);
              }
            });
  }

  private String formaterMessage(
      DelegationPeriodeEvent evenement, User delegue, User adminDelegant) {
    String periode = evenement.dateDebut() + " -> " + evenement.dateFin();
    String delegueNom = delegue.getPrenom() + " " + delegue.getNom();
    String adminNom = adminDelegant.getPrenom() + " " + adminDelegant.getNom();
    return switch (evenement.phase()) {
      case debut ->
          "**Delegation d'approbation active**\n"
              + delegueNom
              + " exerce les droits d'approbation de "
              + adminNom
              + " (periode : "
              + periode
              + "). Adressez-lui vos demandes urgentes.";
      case fin ->
          "**Fin de delegation d'approbation**\n"
              + "La delegation de "
              + adminNom
              + " vers "
              + delegueNom
              + " a pris fin. Les droits d'approbation reviennent a "
              + adminNom
              + ".";
    };
  }
}
