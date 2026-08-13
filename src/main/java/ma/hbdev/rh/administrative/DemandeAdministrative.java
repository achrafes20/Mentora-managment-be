package ma.hbdev.rh.administrative;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "demandes_administratives")
class DemandeAdministrative {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_demande", nullable = false)
  private TypeDemandeAdministrative typeDemande;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private GranulariteConge granularite;

  @Column(name = "date_debut")
  private LocalDate dateDebut;

  @Column(name = "date_fin")
  private LocalDate dateFin;

  @Column(name = "heure_depart")
  private LocalTime heureDepart;

  @Column(name = "heure_retour_prevue")
  private LocalTime heureRetourPrevue;

  @Column(columnDefinition = "TEXT")
  private String motif;

  @Column(name = "fichier_justificatif_id")
  private UUID fichierJustificatifId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutDemandeAdministrative statut = StatutDemandeAdministrative.en_attente;

  @Column(name = "approuve_rejete_par")
  private UUID approuveRejetePar;

  @Column(name = "date_decision")
  private Instant dateDecision;

  @Column(name = "cree_par")
  private UUID creePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected DemandeAdministrative() {}

  DemandeAdministrative(DemandeAdministrativeRequete requete, UUID creePar) {
    this.employeId = requete.employeId();
    this.typeDemande = requete.typeDemande();
    this.granularite = requete.granularite();
    this.dateDebut = requete.dateDebut();
    this.dateFin = requete.dateFin();
    this.heureDepart = requete.heureDepart();
    this.heureRetourPrevue = requete.heureRetourPrevue();
    this.motif = requete.motif();
    this.fichierJustificatifId = requete.fichierJustificatifId();
    this.creePar = creePar;
  }

  void approuver(UUID decidePar) {
    statut = StatutDemandeAdministrative.approuvee;
    approuveRejetePar = decidePar;
    dateDecision = Instant.now();
  }

  void rejeter(UUID decidePar) {
    statut = StatutDemandeAdministrative.rejetee;
    approuveRejetePar = decidePar;
    dateDecision = Instant.now();
  }

  void annuler(UUID decidePar) {
    statut = StatutDemandeAdministrative.annulee;
    approuveRejetePar = decidePar;
    dateDecision = Instant.now();
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  TypeDemandeAdministrative getTypeDemande() {
    return typeDemande;
  }

  GranulariteConge getGranularite() {
    return granularite;
  }

  LocalDate getDateDebut() {
    return dateDebut;
  }

  LocalDate getDateFin() {
    return dateFin;
  }

  LocalTime getHeureDepart() {
    return heureDepart;
  }

  LocalTime getHeureRetourPrevue() {
    return heureRetourPrevue;
  }

  String getMotif() {
    return motif;
  }

  UUID getFichierJustificatifId() {
    return fichierJustificatifId;
  }

  StatutDemandeAdministrative getStatut() {
    return statut;
  }

  UUID getApprouveRejetePar() {
    return approuveRejetePar;
  }

  Instant getDateDecision() {
    return dateDecision;
  }

  UUID getCreePar() {
    return creePar;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
