package ma.hbdev.rh.administrative;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mouvements_conges")
class MouvementCongeAdm {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "demande_id")
  private UUID demandeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_mouvement", nullable = false)
  private TypeMouvementCongeAdm typeMouvement;

  @Column(name = "quantite_jours", nullable = false)
  private BigDecimal quantiteJours;

  @Column(name = "date_mouvement", nullable = false)
  private LocalDate dateMouvement = LocalDate.now();

  @Column private String commentaire;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected MouvementCongeAdm() {}

  MouvementCongeAdm(
      UUID employeId,
      UUID demandeId,
      TypeMouvementCongeAdm typeMouvement,
      BigDecimal quantiteJours,
      String commentaire,
      UUID creePar) {
    this.employeId = employeId;
    this.demandeId = demandeId;
    this.typeMouvement = typeMouvement;
    this.quantiteJours = quantiteJours;
    this.commentaire = commentaire;
    this.creePar = creePar;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  UUID getDemandeId() {
    return demandeId;
  }

  TypeMouvementCongeAdm getTypeMouvement() {
    return typeMouvement;
  }

  BigDecimal getQuantiteJours() {
    return quantiteJours;
  }

  LocalDate getDateMouvement() {
    return dateMouvement;
  }

  String getCommentaire() {
    return commentaire;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
