package ma.hbdev.rh.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Un lot d'import (dry-run ou réel) — EF-EMP-07, écran "journal des imports". */
@Entity
@Table(name = "imports_lots")
class ImportLot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private ImportCible cible;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private ModeImportLot mode;

  @Column(name = "nom_fichier", nullable = false)
  private String nomFichier;

  @Column(name = "nb_lignes_total", nullable = false)
  private int nbLignesTotal;

  @Column(name = "nb_lignes_valides", nullable = false)
  private int nbLignesValides;

  @Column(name = "nb_lignes_erreur", nullable = false)
  private int nbLignesErreur;

  @Column(name = "execute_par")
  private UUID executePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected ImportLot() {}

  ImportLot(
      ImportCible cible,
      ModeImportLot mode,
      String nomFichier,
      int nbLignesTotal,
      int nbLignesValides,
      int nbLignesErreur,
      UUID executePar) {
    this.cible = cible;
    this.mode = mode;
    this.nomFichier = nomFichier;
    this.nbLignesTotal = nbLignesTotal;
    this.nbLignesValides = nbLignesValides;
    this.nbLignesErreur = nbLignesErreur;
    this.executePar = executePar;
  }

  UUID getId() {
    return id;
  }

  ImportCible getCible() {
    return cible;
  }

  ModeImportLot getMode() {
    return mode;
  }

  String getNomFichier() {
    return nomFichier;
  }

  int getNbLignesTotal() {
    return nbLignesTotal;
  }

  int getNbLignesValides() {
    return nbLignesValides;
  }

  int getNbLignesErreur() {
    return nbLignesErreur;
  }

  UUID getExecutePar() {
    return executePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
