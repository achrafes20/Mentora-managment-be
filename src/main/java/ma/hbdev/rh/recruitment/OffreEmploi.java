package ma.hbdev.rh.recruitment;

import com.fasterxml.jackson.databind.JsonNode;
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
@Table(name = "offres_emploi")
class OffreEmploi {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String intitule;

  @Column(columnDefinition = "TEXT")
  private String description;

  // UUID brut, pas de relation JPA vers Departement (package-privé dans le module `employee` —
  // ai-instructions.md règle 4 : jamais de dépendance directe vers l'entité d'une autre feature).
  @Column(name = "departement_id", nullable = false)
  private UUID departementId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutOffreEmploi statut = StatutOffreEmploi.ouverte;

  // EF-REC-12 : utilisé pour le matching de réactivation des candidatures "en_attente".
  @Column(name = "mots_cles_requis")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode motsClesRequis;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  @Column(name = "fermee_le")
  private Instant fermeeLe;

  protected OffreEmploi() {}

  OffreEmploi(
      String intitule,
      String description,
      UUID departementId,
      JsonNode motsClesRequis,
      UUID creePar) {
    this.intitule = intitule;
    this.description = description;
    this.departementId = departementId;
    this.motsClesRequis = motsClesRequis;
    this.creePar = creePar;
  }

  void modifier(String intitule, String description, UUID departementId, JsonNode motsClesRequis) {
    this.intitule = intitule;
    this.description = description;
    this.departementId = departementId;
    this.motsClesRequis = motsClesRequis;
  }

  void fermer() {
    this.statut = StatutOffreEmploi.fermee;
    this.fermeeLe = Instant.now();
  }

  void rouvrir() {
    this.statut = StatutOffreEmploi.ouverte;
    this.fermeeLe = null;
  }

  UUID getId() {
    return id;
  }

  String getIntitule() {
    return intitule;
  }

  String getDescription() {
    return description;
  }

  UUID getDepartementId() {
    return departementId;
  }

  StatutOffreEmploi getStatut() {
    return statut;
  }

  JsonNode getMotsClesRequis() {
    return motsClesRequis;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }

  Instant getFermeeLe() {
    return fermeeLe;
  }
}
