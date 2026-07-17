package ma.hbdev.rh.recruitment;

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

/**
 * EF-REC-08/09 : entretien conduit par le Manager du département visé par l'offre.
 *
 * <p>{@code dateEntretien} est la date/heure planifiée (saisie par l'Admin, modifiable via {@link
 * #reprogrammer} tant qu'aucun résultat n'a été rendu) ; {@code dateResultat} horodate le moment où
 * le Manager soumet réellement son résultat — les deux ne sont jamais confondues.
 */
@Entity
@Table(name = "entretiens")
class Entretien {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "candidature_id", nullable = false)
  private UUID candidatureId;

  @Column(name = "manager_id", nullable = false)
  private UUID managerId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column
  private ResultatEntretien resultat;

  @Column(columnDefinition = "TEXT")
  private String commentaire;

  @Column(name = "date_entretien")
  private Instant dateEntretien;

  @Column(name = "date_resultat")
  private Instant dateResultat;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected Entretien() {}

  Entretien(UUID candidatureId, UUID managerId, Instant dateEntretien) {
    this.candidatureId = candidatureId;
    this.managerId = managerId;
    this.dateEntretien = dateEntretien;
  }

  // EF-REC-09 : tant qu'aucun résultat n'a été rendu, l'Admin peut réassigner le manager et/ou
  // décaler la date planifiée.
  void reprogrammer(UUID nouveauManagerId, Instant nouvelleDateEntretien) {
    if (nouveauManagerId != null) {
      this.managerId = nouveauManagerId;
    }
    if (nouvelleDateEntretien != null) {
      this.dateEntretien = nouvelleDateEntretien;
    }
  }

  void enregistrerResultat(ResultatEntretien resultat, String commentaire) {
    this.resultat = resultat;
    this.commentaire = commentaire;
    this.dateResultat = Instant.now();
  }

  boolean resultatDejaRendu() {
    return resultat != null;
  }

  UUID getId() {
    return id;
  }

  UUID getCandidatureId() {
    return candidatureId;
  }

  UUID getManagerId() {
    return managerId;
  }

  ResultatEntretien getResultat() {
    return resultat;
  }

  String getCommentaire() {
    return commentaire;
  }

  Instant getDateEntretien() {
    return dateEntretien;
  }

  Instant getDateResultat() {
    return dateResultat;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
