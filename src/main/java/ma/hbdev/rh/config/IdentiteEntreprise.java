package ma.hbdev.rh.config;

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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "identite_entreprise")
@Getter
@Setter
class IdentiteEntreprise {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "raison_sociale")
  private String raisonSociale;

  @Column(name = "adresse")
  private String adresse;

  @Column(name = "telephone")
  private String telephone;

  @Column(name = "email")
  private String email;

  @Column(name = "ice")
  private String ice;

  @Column(name = "rc")
  private String rc;

  @Column(name = "ville")
  private String ville;

  @Column(name = "logo_fichier_id")
  private UUID logoFichierId;

  @Column(name = "signature_fichier_id")
  private UUID signatureFichierId;

  @Column(name = "signataire_nom")
  private String signataireNom;

  @Column(name = "signataire_fonction")
  private String signataireFonction;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "signataire_sexe")
  private SexeSignataire signataireSexe;

  @Column(name = "modifie_par")
  private UUID modifiePar;

  @Column(name = "modifie_le", nullable = false)
  private Instant modifieLe = Instant.now();
}
