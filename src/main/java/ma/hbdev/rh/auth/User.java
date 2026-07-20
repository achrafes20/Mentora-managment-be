package ma.hbdev.rh.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "mot_de_passe_hash", nullable = false)
  private String motDePasseHash;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "role", nullable = false)
  private RoleUtilisateur role;

  @Column(name = "nom", nullable = false, length = 100)
  private String nom;

  @Column(name = "prenom", nullable = false, length = 100)
  private String prenom;

  @Column(name = "mattermost_user_id", length = 64, unique = true)
  private String mattermostUserId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "statut", nullable = false)
  private StatutActifInactif statut = StatutActifInactif.actif;

  @Column(name = "tentatives_echouees_consecutives", nullable = false)
  private int tentativesEchoueesConsecutives = 0;

  @Column(name = "verrouille_jusqu_a")
  private Instant verrouilleJusquA;

  @Column(name = "derniere_connexion_le")
  private Instant derniereConnexionLe;

  @Column(name = "cree_le", nullable = false, updatable = false)
  private Instant creeLe = Instant.now();

  @Column(name = "modifie_le", nullable = false)
  private Instant modifieLe = Instant.now();

  public boolean isLocked() {
    return verrouilleJusquA != null && verrouilleJusquA.isAfter(Instant.now());
  }

  public boolean isActive() {
    return statut == StatutActifInactif.actif;
  }
}
