package ma.hbdev.rh.employee;

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

/**
 * Mappage minimal de {@code mouvements_conges}, porté par l'import (EF-EMP-07 : soldes de congés
 * initiaux). La table existe depuis V1 mais n'a pas encore d'entité JPA — le ledger complet
 * (consommation à l'approbation, recrédit à l'annulation, cf. EF-ADM) est le livrable de T3.A2
 * (Achraf, module Demandes administratives) ; cette entité ne couvre que ce dont l'import a besoin
 * (lignes {@code initialisation}), à coordonner avec Achraf avant T3.A2.
 */
@Entity
@Table(name = "mouvements_conges")
class MouvementConge {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_mouvement", nullable = false)
  private TypeMouvementConge typeMouvement;

  @Column(name = "quantite_jours", nullable = false)
  private BigDecimal quantiteJours;

  @Column(name = "date_mouvement", nullable = false)
  private LocalDate dateMouvement = LocalDate.now();

  @Column private String commentaire;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected MouvementConge() {}

  MouvementConge(
      UUID employeId,
      TypeMouvementConge typeMouvement,
      BigDecimal quantiteJours,
      String commentaire,
      UUID creePar) {
    this.employeId = employeId;
    this.typeMouvement = typeMouvement;
    this.quantiteJours = quantiteJours;
    this.commentaire = commentaire;
    this.creePar = creePar;
  }

  void mettreAJour(BigDecimal quantiteJours, String commentaire) {
    this.quantiteJours = quantiteJours;
    this.commentaire = commentaire;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  BigDecimal getQuantiteJours() {
    return quantiteJours;
  }

  String getCommentaire() {
    return commentaire;
  }
}
