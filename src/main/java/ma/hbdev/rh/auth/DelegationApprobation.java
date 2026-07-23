package ma.hbdev.rh.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/** EF-AUTH-11→15 : délégation temporaire des droits d'approbation d'un Admin vers un délégué. */
@Entity
@Table(name = "delegations_approbation")
@Getter
@Setter
public class DelegationApprobation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "admin_delegant_id", nullable = false)
  private UUID adminDelegantId;

  @Column(name = "delegue_id", nullable = false)
  private UUID delegueId;

  @Column(name = "date_debut", nullable = false)
  private LocalDate dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDate dateFin;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "statut", nullable = false)
  private StatutDelegation statut = StatutDelegation.active;

  @Column(name = "revoque_par")
  private UUID revoqueParId;

  @Column(name = "revoque_le")
  private Instant revoqueLe;

  @Column(name = "cree_le", nullable = false, updatable = false)
  private Instant creeLe = Instant.now();

  /**
   * EF-AUTH-13 : expiration calculée à la lecture, jamais persistée — une ligne dont le statut
   * stocké est encore "active" mais dont date_fin est dépassée est présentée comme "expiree" sans
   * écriture en base (choix acté : pas de cron/n8n dédié pour ce seul besoin d'affichage).
   */
  public StatutDelegation statutEffectif() {
    if (statut == StatutDelegation.active && dateFin.isBefore(LocalDate.now())) {
      return StatutDelegation.expiree;
    }
    return statut;
  }

  /**
   * Vrai si la délégation accorde effectivement les droits aujourd'hui (borne de début incluse).
   */
  public boolean estEffectivementActive() {
    LocalDate aujourdHui = LocalDate.now();
    return statut == StatutDelegation.active
        && !aujourdHui.isBefore(dateDebut)
        && !aujourdHui.isAfter(dateFin);
  }
}
