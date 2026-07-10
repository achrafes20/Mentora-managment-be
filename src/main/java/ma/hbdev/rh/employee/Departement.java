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

@Entity
@Table(name = "departements")
class Departement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String nom;

  @Column(name = "manager_id")
  private UUID managerId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutActifInactif statut = StatutActifInactif.actif;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  @Column(name = "modifie_le", insertable = false, updatable = false)
  private Instant modifieLe;

  protected Departement() {}

  Departement(String nom, UUID managerId) {
    this.nom = nom;
    this.managerId = managerId;
  }

  UUID getId() {
    return id;
  }

  String getNom() {
    return nom;
  }

  void setNom(String nom) {
    this.nom = nom;
  }

  UUID getManagerId() {
    return managerId;
  }

  void setManagerId(UUID managerId) {
    this.managerId = managerId;
  }

  StatutActifInactif getStatut() {
    return statut;
  }

  void desactiver() {
    this.statut = StatutActifInactif.inactif;
  }

  Instant getCreeLe() {
    return creeLe;
  }

  Instant getModifieLe() {
    return modifieLe;
  }
}
