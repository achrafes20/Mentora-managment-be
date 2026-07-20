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

@Entity
@Table(name = "jours_feries")
class JourFerie {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "date_ferie", nullable = false, unique = true)
  private LocalDate dateFerie;

  @Column(nullable = false, length = 150)
  private String libelle;

  @Column(name = "gere_par")
  private UUID gerePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected JourFerie() {}

  JourFerie(LocalDate dateFerie, String libelle, UUID gerePar) {
    this.dateFerie = dateFerie;
    this.libelle = libelle;
    this.gerePar = gerePar;
  }

  void modifier(String libelle, UUID gerePar) {
    this.libelle = libelle;
    this.gerePar = gerePar;
  }

  UUID getId() {
    return id;
  }

  LocalDate getDateFerie() {
    return dateFerie;
  }

  String getLibelle() {
    return libelle;
  }

  UUID getGerePar() {
    return gerePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
