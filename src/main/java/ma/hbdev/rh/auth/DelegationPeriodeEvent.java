package ma.hbdev.rh.auth;

import java.time.LocalDate;
import java.util.UUID;

/**
 * EF-AUTH-15 : démarrage ou fin d'une période de délégation active — écouté par {@link
 * DelegationMattermostNotifier} pour prévenir l'ensemble des Managers. Volontairement distinct de
 * {@link ma.hbdev.rh.shared.event.EvenementMetier} : l'EF ne demande qu'un ping Mattermost diffusé
 * à plusieurs destinataires, pas une notification in-app ni une ligne d'audit par Manager notifié
 * (la création/révocation de la délégation elle-même reste, elle, un seul événement métier
 * classique — cf. DelegationService).
 */
record DelegationPeriodeEvent(
    UUID delegationId,
    Phase phase,
    UUID adminDelegantId,
    UUID delegueId,
    LocalDate dateDebut,
    LocalDate dateFin) {

  enum Phase {
    debut,
    fin
  }
}
