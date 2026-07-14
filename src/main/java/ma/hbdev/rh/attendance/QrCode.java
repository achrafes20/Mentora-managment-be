package ma.hbdev.rh.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** QR code actif pour un employé (EF-ATT-01). Un seul actif par employé à la fois. */
@Entity
@Table(name = "qr_codes")
class QrCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(nullable = false, unique = true, length = 255)
  private String valeur;

  @Column(nullable = false)
  private boolean actif = true;

  @Column(nullable = false)
  private boolean bloque = false;

  @Column(name = "bloque_le")
  private Instant bloqueLe;

  @Column(name = "bloque_par")
  private UUID bloquePar;

  @Column(name = "genere_le", insertable = false, updatable = false)
  private Instant genereLe;

  protected QrCode() {}

  QrCode(UUID employeId, String valeur) {
    this.employeId = employeId;
    this.valeur = valeur;
  }

  void revoquer() {
    this.actif = false;
  }

  void bloquer(UUID bloquePar) {
    this.bloque = true;
    this.actif = false;
    this.bloquePar = bloquePar;
    this.bloqueLe = Instant.now();
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  String getValeur() {
    return valeur;
  }

  boolean isActif() {
    return actif;
  }

  boolean isBloque() {
    return bloque;
  }

  Instant getBloqueLe() {
    return bloqueLe;
  }

  UUID getBloquePar() {
    return bloquePar;
  }

  Instant getGenereLe() {
    return genereLe;
  }
}
