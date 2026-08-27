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

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column
  private SexeEmploye sexe;

  // Affichée sur l'attestation de travail (EF-DOC, document actif) — optionnelle, aucune reprise
  // de données réelle par migration (V17).
  @Column private String cin;

  // EF-EMP-01 : optionnel, saisi à la création pour un STAGIAIRE/STAGIAIRE_REMUNERE. Sans objet
  // pour les autres types de contrat.
  @Column(name = "sujet_stage")
  private String sujetStage;

  // EF-EMP-05/EF-REC-13 : renseigné uniquement quand la fiche est créée depuis une candidature
  // recrutement passée au statut "Embauché" — jamais de ligne créée automatiquement, l'Admin
  // complète et soumet le formulaire normal (cf. plan T3.B1, décision verrouillée avec Taha :
  // pas de champ requis employes.* devinable depuis un CV, pas de ligne "incomplète").
  @Column(name = "candidature_origine_id")
  private UUID candidatureOrigineId;

  // EF-EMP-14 : conformité RH Maroc — identifiants organismes sociaux, RIB et fin de période
  // d'essai. Tous optionnels (non connus/applicables à la création selon le profil).
  @Column(name = "numero_cnss")
  private String numeroCnss;

  @Column(name = "numero_amo")
  private String numeroAmo;

  @Column(name = "numero_cimr")
  private String numeroCimr;

  @Column private String rib;

  @Column(name = "periode_essai_fin_le")
  private LocalDate periodeEssaiFinLe;

  // EF-DOC-14 : nécessaire pour générer l'attestation de salaire — optionnel, jamais affiché sur
  // les autres certificats/attestations.
  @Column(name = "salaire_brut_mensuel")
  private java.math.BigDecimal salaireBrutMensuel;

  // EF-EMP-18 : lien vers le compte de connexion correspondant, uniquement renseigné pour les
  // fiches créées via UserService#create (Manager) — un employé ordinaire n'a pas de compte.
  @Column(name = "utilisateur_id")
  private UUID utilisateurId;

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
      UUID candidatureOrigineId,
      SexeEmploye sexe,
      String cin,
      String sujetStage,
      String numeroCnss,
      String numeroAmo,
      String numeroCimr,
      String rib,
      LocalDate periodeEssaiFinLe) {
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
    this.sexe = sexe;
    this.cin = cin;
    this.sujetStage = sujetStage;
    this.numeroCnss = numeroCnss;
    this.numeroAmo = numeroAmo;
    this.numeroCimr = numeroCimr;
    this.rib = rib;
    this.periodeEssaiFinLe = periodeEssaiFinLe;
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
      LocalDate dateFinStagePrevue,
      SexeEmploye sexe,
      String cin,
      String sujetStage,
      String numeroCnss,
      String numeroAmo,
      String numeroCimr,
      String rib,
      LocalDate periodeEssaiFinLe) {
    this.nom = nom;
    this.prenom = prenom;
    this.email = email;
    this.telephone = telephone;
    this.poste = poste;
    this.dateEmbauche = dateEmbauche;
    this.typeContrat = typeContrat;
    this.dateFinContratPrevue = dateFinContratPrevue;
    this.dateFinStagePrevue = dateFinStagePrevue;
    this.sexe = sexe;
    this.cin = cin;
    this.sujetStage = sujetStage;
    this.numeroCnss = numeroCnss;
    this.numeroAmo = numeroAmo;
    this.numeroCimr = numeroCimr;
    this.rib = rib;
    this.periodeEssaiFinLe = periodeEssaiFinLe;
  }

  // EF-DOC-14 : champ sensible, à part du formulaire fiche standard — même principe que
  // definirSujetStage (setter dédié plutôt qu'un 21e paramètre dans modifier()).
  void definirSalaireBrutMensuel(java.math.BigDecimal salaireBrutMensuel) {
    this.salaireBrutMensuel = salaireBrutMensuel;
  }

  // EF-EMP-18 : posé une seule fois à la création (cf. EmployeService#creerPourUtilisateur),
  // jamais modifié ensuite.
  void definirUtilisateurId(UUID utilisateurId) {
    this.utilisateurId = utilisateurId;
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

  void definirSujetStage(String sujetStage) {
    this.sujetStage = sujetStage;
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

  SexeEmploye getSexe() {
    return sexe;
  }

  String getCin() {
    return cin;
  }

  String getSujetStage() {
    return sujetStage;
  }

  UUID getCandidatureOrigineId() {
    return candidatureOrigineId;
  }

  String getNumeroCnss() {
    return numeroCnss;
  }

  String getNumeroAmo() {
    return numeroAmo;
  }

  String getNumeroCimr() {
    return numeroCimr;
  }

  String getRib() {
    return rib;
  }

  LocalDate getPeriodeEssaiFinLe() {
    return periodeEssaiFinLe;
  }

  java.math.BigDecimal getSalaireBrutMensuel() {
    return salaireBrutMensuel;
  }

  UUID getUtilisateurId() {
    return utilisateurId;
  }

  Instant getCreeLe() {
    return creeLe;
  }

  Instant getModifieLe() {
    return modifieLe;
  }
}
