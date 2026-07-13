package ma.hbdev.rh.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** EF-EMP-11 — trace historisée d'un transfert département/manager, jamais modifiée après coup. */
@Entity
@Table(name = "employe_transferts")
class EmployeTransfert {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "ancien_departement_id")
  private UUID ancienDepartementId;

  @Column(name = "nouveau_departement_id")
  private UUID nouveauDepartementId;

  @Column(name = "ancien_manager_id")
  private UUID ancienManagerId;

  @Column(name = "nouveau_manager_id")
  private UUID nouveauManagerId;

  @Column(name = "date_effet", nullable = false)
  private LocalDate dateEffet;

  @Column(name = "effectue_par")
  private UUID effectuePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected EmployeTransfert() {}

  EmployeTransfert(
      UUID employeId,
      UUID ancienDepartementId,
      UUID nouveauDepartementId,
      UUID ancienManagerId,
      UUID nouveauManagerId,
      LocalDate dateEffet,
      UUID effectuePar) {
    this.employeId = employeId;
    this.ancienDepartementId = ancienDepartementId;
    this.nouveauDepartementId = nouveauDepartementId;
    this.ancienManagerId = ancienManagerId;
    this.nouveauManagerId = nouveauManagerId;
    this.dateEffet = dateEffet;
    this.effectuePar = effectuePar;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  UUID getAncienDepartementId() {
    return ancienDepartementId;
  }

  UUID getNouveauDepartementId() {
    return nouveauDepartementId;
  }

  UUID getAncienManagerId() {
    return ancienManagerId;
  }

  UUID getNouveauManagerId() {
    return nouveauManagerId;
  }

  LocalDate getDateEffet() {
    return dateEffet;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
