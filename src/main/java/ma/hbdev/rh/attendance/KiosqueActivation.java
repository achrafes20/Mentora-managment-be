package ma.hbdev.rh.attendance;

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
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/**
 * NFR-UX-02 : jeton d'activation par appareil pour le kiosque de pointage. Une ligne = un code
 * généré par l'Admin (ou un délégué actif) ; la même ligne devient l'enregistrement d'activation
 * permanent de l'appareil une fois le code accepté (statut passe de {@code en_attente} à {@code
 * active}).
 */
@Entity
@Table(name = "kiosque_activations")
@Getter
class KiosqueActivation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "code_hash", nullable = false)
  private String codeHash;

  @Column(name = "emis_par", nullable = false)
  private UUID emisPar;

  @Column(name = "delegation_id")
  private UUID delegationId;

  @Column(name = "emis_le", insertable = false, updatable = false)
  private Instant emisLe;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private StatutActivationKiosque statut = StatutActivationKiosque.en_attente;

  @Column(name = "device_token_hash")
  private String deviceTokenHash;

  @Column(name = "activee_le")
  private Instant activeeLe;

  @Column(name = "revoquee_le")
  private Instant revoqueeLe;

  @Column(name = "revoquee_par")
  private UUID revoqueePar;

  @Column(name = "tentatives_echouees_consecutives", nullable = false)
  private int tentativesEchoueesConsecutives = 0;

  @Column(name = "verrouille_jusqu_a")
  private Instant verrouilleJusquA;

  protected KiosqueActivation() {}

  KiosqueActivation(String codeHash, UUID emisPar, UUID delegationId) {
    this.codeHash = codeHash;
    this.emisPar = emisPar;
    this.delegationId = delegationId;
  }

  boolean isVerrouillee() {
    return verrouilleJusquA != null && verrouilleJusquA.isAfter(Instant.now());
  }

  /**
   * Même algorithme que {@code AuthService#handleFailedAttempt} (verrouillage de compte), appliqué
   * ici à ce code plutôt qu'à un utilisateur.
   */
  void enregistrerEchec(int tentativesMax, int delaiDeverrouillageMinutes) {
    tentativesEchoueesConsecutives++;
    if (tentativesEchoueesConsecutives >= tentativesMax) {
      verrouilleJusquA = Instant.now().plusSeconds((long) delaiDeverrouillageMinutes * 60);
    }
  }

  void activer(String deviceTokenHash) {
    this.statut = StatutActivationKiosque.active;
    this.deviceTokenHash = deviceTokenHash;
    this.activeeLe = Instant.now();
    this.tentativesEchoueesConsecutives = 0;
    this.verrouilleJusquA = null;
  }

  /** Désactivation manuelle — pas d'expiration automatique pour l'instant (choix provisoire). */
  void revoquer(UUID revoqueePar) {
    this.statut = StatutActivationKiosque.revoquee;
    this.revoqueePar = revoqueePar;
    this.revoqueeLe = Instant.now();
    this.deviceTokenHash = null;
  }
}
