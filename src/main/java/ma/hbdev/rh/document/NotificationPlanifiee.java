package ma.hbdev.rh.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications_planifiees")
class NotificationPlanifiee {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_surveillance", nullable = false)
  private TypeFinSurveillee typeSurveillance;

  @Column(name = "date_echeance", nullable = false)
  private LocalDate dateEcheance;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "statut", nullable = false)
  private StatutNotificationPlanifiee statut = StatutNotificationPlanifiee.planifiee;

  @Column(name = "premiere_notification_envoyee_le")
  private Instant premiereNotificationEnvoyeeLe;

  @Column(name = "relance_envoyee_le")
  private Instant relanceEnvoyeeLe;

  @Column(name = "annulee_le")
  private Instant annuleeLe;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected NotificationPlanifiee() {}

  NotificationPlanifiee(
      UUID employeId, TypeFinSurveillee typeSurveillance, LocalDate dateEcheance) {
    this.employeId = employeId;
    this.typeSurveillance = typeSurveillance;
    this.dateEcheance = dateEcheance;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  TypeFinSurveillee getTypeSurveillance() {
    return typeSurveillance;
  }

  LocalDate getDateEcheance() {
    return dateEcheance;
  }

  StatutNotificationPlanifiee getStatut() {
    return statut;
  }

  Instant getPremiereNotificationEnvoyeeLe() {
    return premiereNotificationEnvoyeeLe;
  }

  Instant getRelanceEnvoyeeLe() {
    return relanceEnvoyeeLe;
  }

  void marquerEnvoyee() {
    this.statut = StatutNotificationPlanifiee.envoyee;
    this.premiereNotificationEnvoyeeLe = Instant.now();
  }

  void marquerRelancee() {
    this.statut = StatutNotificationPlanifiee.relancee;
    this.relanceEnvoyeeLe = Instant.now();
  }

  void annuler() {
    this.statut = StatutNotificationPlanifiee.annulee;
    this.annuleeLe = Instant.now();
  }
}
