package ma.hbdev.rh.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "candidatures")
class Candidature {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "offre_id")
  private UUID offreId;

  @Column(length = 100)
  private String nom;

  @Column(length = 100)
  private String prenom;

  @Column(nullable = false)
  private String email;

  @Column(length = 30)
  private String telephone;

  @Column(name = "intitule_poste_detecte", length = 200)
  private String intitulePosteDetecte;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private SourceCandidature source = SourceCandidature.email;

  @Column(name = "cv_fichier_id")
  private UUID cvFichierId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutCandidature statut = StatutCandidature.recu;

  @Column(name = "analyse_courante_id")
  private UUID analyseCouranteId;

  // Relation en lecture seule, gouvernée par `analyseCouranteId` ci-dessus (colonne partagée,
  // insertable/updatable=false pour éviter un double write) — permet le tri/filtre par score
  // (EF-REC-06) sans requête supplémentaire.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "analyse_courante_id", insertable = false, updatable = false)
  private AnalyseIa analyseCourante;

  @Column(name = "date_ingestion", insertable = false, updatable = false)
  private Instant dateIngestion;

  @Column(name = "date_archivage")
  private Instant dateArchivage;

  @Column(name = "source_import_reference", columnDefinition = "TEXT")
  private String sourceImportReference;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected Candidature() {}

  Candidature(
      UUID offreId,
      String nom,
      String prenom,
      String email,
      String telephone,
      String intitulePosteDetecte,
      SourceCandidature source,
      UUID cvFichierId,
      StatutCandidature statutInitial,
      String sourceImportReference) {
    this.offreId = offreId;
    this.nom = nom;
    this.prenom = prenom;
    this.email = email;
    this.telephone = telephone;
    this.intitulePosteDetecte = intitulePosteDetecte;
    this.source = source;
    this.cvFichierId = cvFichierId;
    this.statut = statutInitial;
    this.sourceImportReference = sourceImportReference;
  }

  void changerStatut(StatutCandidature nouveauStatut) {
    this.statut = nouveauStatut;
  }

  void archiver() {
    this.statut = StatutCandidature.archivee;
    this.dateArchivage = Instant.now();
  }

  void assignerOffre(UUID offreId) {
    this.offreId = offreId;
  }

  void definirAnalyseCourante(UUID analyseId) {
    this.analyseCouranteId = analyseId;
  }

  // EF-EMP-05 : les champs extraits par l'IA complètent la fiche candidature seulement s'ils sont
  // absents (ex. ingestion e-mail sans nom exploitable dans l'expéditeur) — jamais d'écrasement
  // d'une donnée de contact déjà connue.
  void completerIdentiteSiAbsente(
      String prenom, String nom, String email, String telephone, String intitulePoste) {
    if (this.prenom == null && prenom != null) {
      this.prenom = prenom;
    }
    if (this.nom == null && nom != null) {
      this.nom = nom;
    }
    if (this.telephone == null && telephone != null) {
      this.telephone = telephone;
    }
    if (this.intitulePosteDetecte == null && intitulePoste != null) {
      this.intitulePosteDetecte = intitulePoste;
    }
  }

  UUID getId() {
    return id;
  }

  UUID getOffreId() {
    return offreId;
  }

  String getNom() {
    return nom;
  }

  String getPrenom() {
    return prenom;
  }

  String getEmail() {
    return email;
  }

  String getTelephone() {
    return telephone;
  }

  String getIntitulePosteDetecte() {
    return intitulePosteDetecte;
  }

  SourceCandidature getSource() {
    return source;
  }

  UUID getCvFichierId() {
    return cvFichierId;
  }

  StatutCandidature getStatut() {
    return statut;
  }

  UUID getAnalyseCouranteId() {
    return analyseCouranteId;
  }

  AnalyseIa getAnalyseCourante() {
    return analyseCourante;
  }

  Instant getDateIngestion() {
    return dateIngestion;
  }

  Instant getDateArchivage() {
    return dateArchivage;
  }

  String getSourceImportReference() {
    return sourceImportReference;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
