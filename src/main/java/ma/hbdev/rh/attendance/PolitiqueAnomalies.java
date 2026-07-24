package ma.hbdev.rh.attendance;

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

/** EF-ATT-11 : seuil d'anomalies non résolues avant notification d'alerte. */
@Entity
@Table(name = "politique_anomalies")
@Getter
@Setter
class PolitiqueAnomalies {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "seuil_anomalies", nullable = false)
  private int seuilAnomalies;

  @Column(name = "periode_jours", nullable = false)
  private int periodeJours;

  @Column(name = "modifie_par")
  private UUID modifiePar;

  @Column(name = "modifie_le", nullable = false)
  private Instant modifieLe = Instant.now();
}
