package ma.hbdev.rh.employee;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employes")
class Employe {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String nom;

  @Column(nullable = false, length = 100)
  private String prenom;

  @Column private String email;

  @Column private String telephone;

  @Column private String poste;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "departement_id", nullable = false)
  private Departement departement;

  @Column(name = "manager_id")
  private UUID managerId;

  @Column(name = "date_embauche", nullable = false)
  private LocalDate dateEmbauche;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_contrat", nullable = false)
  private TypeContratEmploye typeContrat;

  @Column(name = "date_fin_contrat_prevue")
  private LocalDate dateFinContratPrevue;

  @Column(name = "date_fin_stage_prevue")
  private LocalDate dateFinStagePrevue;

  @Column(name = "date_depart")
  private LocalDate dateDepart;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "motif_depart")
  private MotifDepartEmploye motifDepart;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StatutActifInactif statut = StatutActifInactif.actif;

  @Column(name = "photo_fichier_id")
  private UUID photoFichierId;

  // EF-EMP-05/EF-REC-13 : renseigné uniquement quand la fiche est créée depuis une candidature
  // recrutement passée au statut "Embauché" — jamais de ligne créée automatiquement, l'Admin
  // complète et soumet le formulaire normal (cf. plan T3.B1, décision verrouillée avec Taha :
  // pas de champ requis employes.* devinable depuis un CV, pas de ligne "incomplète").
  @Column(name = "candidature_origine_id")
  private UUID candidatureOrigineId;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  @Column(name = "modifie_le", insertable = false, updatable = false)
  private Instant modifieLe;

  protected Employe() {}

  Employe(
      String nom,
      String prenom,
      String email,
      String telephone,
      String poste,
      Departement departement,
      UUID managerId,
      LocalDate dateEmbauche,
      TypeContratEmploye typeContrat,
      LocalDate dateFinContratPrevue,
      LocalDate dateFinStagePrevue,
      UUID candidatureOrigineId) {
    this.nom = nom;
    this.prenom = prenom;
    this.email = email;
    this.telephone = telephone;
    this.poste = poste;
    this.departement = departement;
    this.managerId = managerId;
    this.dateEmbauche = dateEmbauche;
    this.typeContrat = typeContrat;
    this.dateFinContratPrevue = dateFinContratPrevue;
    this.dateFinStagePrevue = dateFinStagePrevue;
    this.candidatureOrigineId = candidatureOrigineId;
  }

  void modifier(
      String nom,
      String prenom,
      String email,
      String telephone,
      String poste,
      LocalDate dateEmbauche,
      TypeContratEmploye typeContrat,
      LocalDate dateFinContratPrevue,
      LocalDate dateFinStagePrevue) {
    this.nom = nom;
    this.prenom = prenom;
    this.email = email;
    this.telephone = telephone;
    this.poste = poste;
    this.dateEmbauche = dateEmbauche;
    this.typeContrat = typeContrat;
    this.dateFinContratPrevue = dateFinContratPrevue;
    this.dateFinStagePrevue = dateFinStagePrevue;
  }

  void transferer(Departement nouveauDepartement, UUID nouveauManagerId) {
    this.departement = nouveauDepartement;
    this.managerId = nouveauManagerId;
  }

  void desactiver(MotifDepartEmploye motif, LocalDate dateDepart) {
    this.statut = StatutActifInactif.inactif;
    this.motifDepart = motif;
    this.dateDepart = dateDepart;
  }

  void definirPhoto(UUID fichierId) {
    this.photoFichierId = fichierId;
  }

  UUID getId() {
    return id;
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

  String getPoste() {
    return poste;
  }

  Departement getDepartement() {
    return departement;
  }

  UUID getManagerId() {
    return managerId;
  }

  LocalDate getDateEmbauche() {
    return dateEmbauche;
  }

  TypeContratEmploye getTypeContrat() {
    return typeContrat;
  }

  LocalDate getDateFinContratPrevue() {
    return dateFinContratPrevue;
  }

  LocalDate getDateFinStagePrevue() {
    return dateFinStagePrevue;
  }

  LocalDate getDateDepart() {
    return dateDepart;
  }

  MotifDepartEmploye getMotifDepart() {
    return motifDepart;
  }

  StatutActifInactif getStatut() {
    return statut;
  }

  UUID getPhotoFichierId() {
    return photoFichierId;
  }

  UUID getCandidatureOrigineId() {
    return candidatureOrigineId;
  }

  Instant getCreeLe() {
    return creeLe;
  }

  Instant getModifieLe() {
    return modifieLe;
  }
}
