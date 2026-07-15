package ma.hbdev.rh.employee;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Rapport ligne par ligne d'un {@link ImportLot} — EF-EMP-07. */
@Entity
@Table(name = "imports_lignes")
class ImportLigne {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "lot_id", nullable = false)
  private UUID lotId;

  @Column(name = "numero_ligne", nullable = false)
  private int numeroLigne;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutLigneImport statut;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private ActionLigneImport action;

  @Column(name = "donnees_brutes", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode donneesBrutes;

  @Column private String erreurs;

  @Column(name = "entite_id")
  private UUID entiteId;

  protected ImportLigne() {}

  ImportLigne(
      UUID lotId,
      int numeroLigne,
      StatutLigneImport statut,
      ActionLigneImport action,
      JsonNode donneesBrutes,
      String erreurs,
      UUID entiteId) {
    this.lotId = lotId;
    this.numeroLigne = numeroLigne;
    this.statut = statut;
    this.action = action;
    this.donneesBrutes = donneesBrutes;
    this.erreurs = erreurs;
    this.entiteId = entiteId;
  }

  UUID getId() {
    return id;
  }

  int getNumeroLigne() {
    return numeroLigne;
  }

  StatutLigneImport getStatut() {
    return statut;
  }

  ActionLigneImport getAction() {
    return action;
  }

  JsonNode getDonneesBrutes() {
    return donneesBrutes;
  }

  String getErreurs() {
    return erreurs;
  }

  UUID getEntiteId() {
    return entiteId;
  }
}
