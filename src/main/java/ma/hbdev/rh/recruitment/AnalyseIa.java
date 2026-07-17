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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Séparée de {@code Candidature} (cf. commentaire schema_v1.sql §6) : une relance (EF-REC-10) crée
 * une nouvelle ligne plutôt que d'écraser la précédente, chaînée via {@code remplaceAnalyseId} —
 * l'historique des échecs/succès est conservé.
 */
@Entity
@Table(name = "analyses_ia")
class AnalyseIa {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "candidature_id", nullable = false)
  private UUID candidatureId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutAnalyseIa statut = StatutAnalyseIa.en_attente;

  @Column(name = "extrait_prenom", length = 100)
  private String extraitPrenom;

  @Column(name = "extrait_nom", length = 100)
  private String extraitNom;

  @Column(name = "extrait_email")
  private String extraitEmail;

  @Column(name = "extrait_telephone", length = 30)
  private String extraitTelephone;

  @Column(name = "extrait_intitule_poste", length = 200)
  private String extraitIntitulePoste;

  @Column(name = "score_correspondance", precision = 5, scale = 2)
  private BigDecimal scoreCorrespondance;

  @Column(name = "annees_experience_estimees", precision = 4, scale = 1)
  private BigDecimal anneesExperienceEstimees;

  @Column(name = "justification_score", columnDefinition = "TEXT")
  private String justificationScore;

  @Column(name = "mots_cles")
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode motsCles;

  @Column(name = "remplace_analyse_id")
  private UUID remplaceAnalyseId;

  @Column(name = "date_analyse", insertable = false, updatable = false)
  private Instant dateAnalyse;

  protected AnalyseIa() {}

  AnalyseIa(
      UUID candidatureId,
      StatutAnalyseIa statut,
      String extraitPrenom,
      String extraitNom,
      String extraitEmail,
      String extraitTelephone,
      String extraitIntitulePoste,
      BigDecimal scoreCorrespondance,
      BigDecimal anneesExperienceEstimees,
      String justificationScore,
      JsonNode motsCles,
      UUID remplaceAnalyseId) {
    this.candidatureId = candidatureId;
    this.statut = statut;
    this.extraitPrenom = extraitPrenom;
    this.extraitNom = extraitNom;
    this.extraitEmail = extraitEmail;
    this.extraitTelephone = extraitTelephone;
    this.extraitIntitulePoste = extraitIntitulePoste;
    this.scoreCorrespondance = scoreCorrespondance;
    this.anneesExperienceEstimees = anneesExperienceEstimees;
    this.justificationScore = justificationScore;
    this.motsCles = motsCles;
    this.remplaceAnalyseId = remplaceAnalyseId;
  }

  UUID getId() {
    return id;
  }

  UUID getCandidatureId() {
    return candidatureId;
  }

  StatutAnalyseIa getStatut() {
    return statut;
  }

  String getExtraitPrenom() {
    return extraitPrenom;
  }

  String getExtraitNom() {
    return extraitNom;
  }

  String getExtraitEmail() {
    return extraitEmail;
  }

  String getExtraitTelephone() {
    return extraitTelephone;
  }

  String getExtraitIntitulePoste() {
    return extraitIntitulePoste;
  }

  BigDecimal getScoreCorrespondance() {
    return scoreCorrespondance;
  }

  BigDecimal getAnneesExperienceEstimees() {
    return anneesExperienceEstimees;
  }

  String getJustificationScore() {
    return justificationScore;
  }

  JsonNode getMotsCles() {
    return motsCles;
  }

  UUID getRemplaceAnalyseId() {
    return remplaceAnalyseId;
  }

  Instant getDateAnalyse() {
    return dateAnalyse;
  }
}
