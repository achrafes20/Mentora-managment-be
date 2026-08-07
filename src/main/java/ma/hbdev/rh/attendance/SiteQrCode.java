package ma.hbdev.rh.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * EF-ATT-17 : QR code d'un lieu de travail (affiché à l'entrée, imprimé ou sur écran) — scanné par
 * le téléphone personnel d'un employé déjà appairé (cf. KiosqueActivation.employeId) pour prouver
 * sa présence physique au lieu au moment du pointage.
 */
@Entity
@Table(name = "sites_qr_codes")
class SiteQrCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String libelle;

  @Column(nullable = false, unique = true, length = 255)
  private String valeur;

  @Column(nullable = false)
  private boolean actif = true;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected SiteQrCode() {}

  SiteQrCode(String libelle, String valeur, UUID creePar) {
    this.libelle = libelle;
    this.valeur = valeur;
    this.creePar = creePar;
  }

  void desactiver() {
    this.actif = false;
  }

  UUID getId() {
    return id;
  }

  String getLibelle() {
    return libelle;
  }

  String getValeur() {
    return valeur;
  }

  boolean isActif() {
    return actif;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
