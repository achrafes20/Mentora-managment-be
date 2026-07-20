package ma.hbdev.rh.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "notifications_mattermost")
class NotificationMattermost {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "destinataire_id", nullable = false)
  private UUID destinataireId;

  @Column(name = "type_evenement", nullable = false, length = 100)
  private String typeEvenement;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ModuleAudit module;

  @Column(name = "entite_type", length = 100)
  private String entiteType;

  @Column(name = "entite_id")
  private UUID entiteId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String contenu;

  @Column(name = "envoyee_le", nullable = false)
  private Instant envoyeeLe = Instant.now();

  @Column(nullable = false)
  private boolean echec;

  @Column(columnDefinition = "TEXT")
  private String erreur;

  protected NotificationMattermost() {}

  NotificationMattermost(
      EvenementMetier evenement, NotificationMetier notification, boolean reussi, String erreur) {
    this.destinataireId = notification.destinataireId();
    this.typeEvenement = notification.typeEvenement();
    this.module = evenement.module();
    this.entiteType = evenement.entiteType();
    this.entiteId = evenement.entiteId();
    this.contenu = notification.titre() + "\n" + notification.message();
    this.echec = !reussi;
    this.erreur = erreur;
  }
}
