package ma.hbdev.rh.attendance;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Planning de télétravail récurrent par employé (EF-ATT-08). */
@Entity
@Table(name = "plannings_teletravail")
class PlanningTeletravail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin")
  private LocalDate dateFin;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  @Column(name = "modifie_le", insertable = false, updatable = false)
  private Instant modifieLe;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "planning_teletravail_id", nullable = false, updatable = false)
  private List<PlanningTeletravailJour> jours = new ArrayList<>();

  protected PlanningTeletravail() {}

  PlanningTeletravail(UUID employeId, LocalDate dateDebut, LocalDate dateFin, UUID creePar) {
    this.employeId = employeId;
    this.dateDebut = dateDebut;
    this.dateFin = dateFin;
    this.creePar = creePar;
  }

  void setJours(List<TypeJourSemaine> joursChoisis) {
    this.jours.clear();
    for (TypeJourSemaine jour : joursChoisis) {
      this.jours.add(new PlanningTeletravailJour(jour));
    }
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  LocalDate getDateDebut() {
    return dateDebut;
  }

  LocalDate getDateFin() {
    return dateFin;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }

  Instant getModifieLe() {
    return modifieLe;
  }

  List<PlanningTeletravailJour> getJours() {
    return jours;
  }
}
