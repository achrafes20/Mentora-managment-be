package ma.hbdev.rh.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Horaire de référence unique pour toute l'entreprise (EF-ATT-07). Historisé : plusieurs lignes
 * peuvent coexister, le service résout l'horaire en vigueur à une date donnée via {@code
 * date_effet}.
 */
@Entity
@Table(name = "horaires_reference")
class HoraireReference {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "heure_debut_matin", nullable = false)
  private LocalTime heureDebutMatin;

  @Column(name = "heure_fin_matin", nullable = false)
  private LocalTime heureFinMatin;

  @Column(name = "heure_debut_apres_midi", nullable = false)
  private LocalTime heureDebutApresMidi;

  @Column(name = "heure_fin_apres_midi", nullable = false)
  private LocalTime heureFinApresMidi;

  @Column(name = "tolerance_minutes", nullable = false)
  private int toleranceMinutes;

  @Column(name = "date_effet", nullable = false)
  private LocalDate dateEffet;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected HoraireReference() {}

  HoraireReference(
      LocalTime heureDebutMatin,
      LocalTime heureFinMatin,
      LocalTime heureDebutApresMidi,
      LocalTime heureFinApresMidi,
      int toleranceMinutes,
      LocalDate dateEffet,
      UUID creePar) {
    this.heureDebutMatin = heureDebutMatin;
    this.heureFinMatin = heureFinMatin;
    this.heureDebutApresMidi = heureDebutApresMidi;
    this.heureFinApresMidi = heureFinApresMidi;
    this.toleranceMinutes = toleranceMinutes;
    this.dateEffet = dateEffet;
    this.creePar = creePar;
  }

  UUID getId() {
    return id;
  }

  LocalTime getHeureDebutMatin() {
    return heureDebutMatin;
  }

  LocalTime getHeureFinMatin() {
    return heureFinMatin;
  }

  LocalTime getHeureDebutApresMidi() {
    return heureDebutApresMidi;
  }

  LocalTime getHeureFinApresMidi() {
    return heureFinApresMidi;
  }

  int getToleranceMinutes() {
    return toleranceMinutes;
  }

  LocalDate getDateEffet() {
    return dateEffet;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
