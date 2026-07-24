package ma.hbdev.rh.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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

  @Column(name = "logo_fichier_id")
  private UUID logoFichierId;

  @Column(name = "modifie_par")
  private UUID modifiePar;

  @Column(name = "modifie_le", nullable = false)
  private Instant modifieLe = Instant.now();
}
