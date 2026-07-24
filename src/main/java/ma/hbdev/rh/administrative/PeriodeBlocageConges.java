package ma.hbdev.rh.administrative;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** EF-ADM-12 : période durant laquelle aucune nouvelle demande de congé ne peut être créée. */
@Entity
@Table(name = "periodes_blocage_conges")
class PeriodeBlocageConges {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDate dateFin;

  @Column(nullable = false, length = 150)
  private String libelle;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected PeriodeBlocageConges() {}

  PeriodeBlocageConges(LocalDate dateDebut, LocalDate dateFin, String libelle, UUID creePar) {
    this.dateDebut = dateDebut;
    this.dateFin = dateFin;
    this.libelle = libelle;
    this.creePar = creePar;
  }

  UUID getId() {
    return id;
  }

  LocalDate getDateDebut() {
    return dateDebut;
  }

  LocalDate getDateFin() {
    return dateFin;
  }

  String getLibelle() {
    return libelle;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
