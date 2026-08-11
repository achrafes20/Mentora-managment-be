package ma.hbdev.rh.attendance;

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

/** Anomalie de pointage détectée par le moteur (EF-ATT-04). */
@Entity
@Table(name = "anomalies_pointage")
class AnomaliePointage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "date_pointage", nullable = false)
  private LocalDate datePointage;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_anomalie", nullable = false)
  private TypeAnomaliePointage typeAnomalie;

  @Column(name = "pointage_entree_id")
  private UUID pointageEntreeId;

  @Column(name = "pointage_sortie_id")
  private UUID pointageSortieId;

  @Column(nullable = false)
  private boolean resolue = false;

  /** EF-ATT-11 : cette anomalie est celle qui a fait franchir le seuil et déclenché l'alerte. */
  @Column(name = "a_declenche_alerte_seuil", nullable = false)
  private boolean aDeclencheAlerteSeuil = false;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected AnomaliePointage() {}

  AnomaliePointage(
      UUID employeId,
      LocalDate datePointage,
      TypeAnomaliePointage typeAnomalie,
      UUID pointageEntreeId,
      UUID pointageSortieId) {
    this.employeId = employeId;
    this.datePointage = datePointage;
    this.typeAnomalie = typeAnomalie;
    this.pointageEntreeId = pointageEntreeId;
    this.pointageSortieId = pointageSortieId;
  }

  void marquerResolue() {
    this.resolue = true;
  }

  void marquerADeclencheAlerteSeuil() {
    this.aDeclencheAlerteSeuil = true;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  LocalDate getDatePointage() {
    return datePointage;
  }

  TypeAnomaliePointage getTypeAnomalie() {
    return typeAnomalie;
  }

  UUID getPointageEntreeId() {
    return pointageEntreeId;
  }

  UUID getPointageSortieId() {
    return pointageSortieId;
  }

  boolean isResolue() {
    return resolue;
  }

  boolean isADeclencheAlerteSeuil() {
    return aDeclencheAlerteSeuil;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
