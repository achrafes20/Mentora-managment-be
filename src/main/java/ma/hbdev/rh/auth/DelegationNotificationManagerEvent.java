package ma.hbdev.rh.auth;

import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;

/**
 * EF-AUTH-15 : ping à un Manager actif (in-app + Mattermost) au démarrage/à la fin d'une délégation
 * — un événement par destinataire, publié via le mécanisme général à deux canaux ({@code
 * InAppNotificationEventListener}/{@code MattermostNotificationEventListener}).
 *
 * <p>Remplace un appel direct au client Mattermost qui ignorait silencieusement {@link
 * ma.hbdev.rh.shared.mattermost.ResultatMattermost}, l'échec renvoyé (pas une exception) — ni
 * loggé, ni persisté, et sans notification in-app de repli. En passant par le même mécanisme que le
 * reste de l'application, l'échec Mattermost est désormais persisté (EF-NOTIF-06) et l'in-app sert
 * de canal garanti (EF-NOTIF-01) même si Mattermost n'est pas configuré.
 *
 * <p>Volontairement non audité ({@link #audite()}) : le fait générateur (création/révocation/
 * expiration de la délégation) est déjà tracé une seule fois par {@link DelegationModifieeEvent} —
 * auditer aussi chaque envoi par destinataire dupliquerait la même ligne autant de fois qu'il y a
 * de Managers actifs.
 */
record DelegationNotificationManagerEvent(UUID delegationId, NotificationMetier notification)
    implements EvenementMetier {

  @Override
  public String action() {
    return "notification_manager";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.delegation;
  }

  @Override
  public String entiteType() {
    return "delegation_approbation";
  }

  @Override
  public UUID entiteId() {
    return delegationId;
  }

  // notification() : pas de surcharge nécessaire, l'accesseur canonique du composant
  // "notification" du record satisfait déjà la méthode d'interface du même nom.

  @Override
  public boolean audite() {
    return false;
  }
}
