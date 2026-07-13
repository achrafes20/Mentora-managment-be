package ma.hbdev.rh.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reinitialisations_mot_de_passe")
@Getter
@Setter
public class PasswordReset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "utilisateur_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expire_le", nullable = false)
  private Instant expireLe;

  @Column(name = "utilise", nullable = false)
  private boolean utilise = false;

  @Column(name = "cree_le", nullable = false, updatable = false)
  private Instant creeLe = Instant.now();

  public boolean isValid() {
    return !utilise && expireLe.isAfter(Instant.now());
  }
}
