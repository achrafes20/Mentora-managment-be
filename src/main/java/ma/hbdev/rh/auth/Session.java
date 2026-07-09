package ma.hbdev.rh.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sessions_utilisateur")
@Getter
@Setter
public class Session {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "utilisateur_id", nullable = false)
  private User user;

  @Column(name = "jeton_hash", nullable = false)
  private String jetonHash;

  @Column(name = "expire_le", nullable = false)
  private Instant expireLe;

  @Column(name = "derniere_activite_le", nullable = false)
  private Instant derniereActiviteLe = Instant.now();

  @Column(name = "revoque_le")
  private Instant revoqueLe;

  @Column(name = "cree_le", nullable = false, updatable = false)
  private Instant creeLe = Instant.now();

  public boolean isValid() {
    return revoqueLe == null && expireLe.isAfter(Instant.now());
  }
}
