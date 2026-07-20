package ma.hbdev.rh.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.event.NotificationMetier;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "notifications_in_app")
class NotificationInApp {

  @Id private UUID id;

  @Column(name = "destinataire_id", nullable = false)
  private UUID destinataireId;

  @Column(nullable = false, length = 200)
  private String titre;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ModuleAudit module;

  @Column(name = "lien_action", columnDefinition = "TEXT")
  private String lienAction;

  @Column(name = "entite_type", length = 100)
  private String entiteType;

  @Column(name = "entite_id")
  private UUID entiteId;

  @Column(nullable = false)
  private boolean lu;

  @Column(name = "lu_le")
  private Instant luLe;

  @Column(name = "mattermost_tente", nullable = false)
  private boolean mattermostTente;

  @Column(name = "mattermost_reussi")
  private Boolean mattermostReussi;

  @Column(name = "mattermost_erreur", columnDefinition = "TEXT")
  private String mattermostErreur;

  @Column(name = "archivee_le")
  private Instant archiveeLe;

  @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
  private Instant creeLe;

  protected NotificationInApp() {}

  NotificationInApp(
      NotificationMetier notification, ModuleAudit module, String entiteType, UUID entiteId) {
    this.id = notification.id();
    this.destinataireId = notification.destinataireId();
    this.titre = notification.titre();
    this.message = notification.message();
    this.module = module;
    this.lienAction = notification.lienAction();
    this.entiteType = entiteType;
    this.entiteId = entiteId;
  }

  void marquerLue() {
    if (!lu) {
      lu = true;
      luLe = Instant.now();
    }
  }

  void enregistrerResultatMattermost(boolean reussi, String erreur) {
    mattermostTente = true;
    mattermostReussi = reussi;
    mattermostErreur = erreur;
  }

  UUID getId() {
    return id;
  }

  UUID getDestinataireId() {
    return destinataireId;
  }

  String getTitre() {
    return titre;
  }

  String getMessage() {
    return message;
  }

  ModuleAudit getModule() {
    return module;
  }

  String getLienAction() {
    return lienAction;
  }

  String getEntiteType() {
    return entiteType;
  }

  UUID getEntiteId() {
    return entiteId;
  }

  boolean isLu() {
    return lu;
  }

  Instant getLuLe() {
    return luLe;
  }

  boolean isMattermostTente() {
    return mattermostTente;
  }

  Boolean getMattermostReussi() {
    return mattermostReussi;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
