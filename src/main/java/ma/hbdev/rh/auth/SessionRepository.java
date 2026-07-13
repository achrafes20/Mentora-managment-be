package ma.hbdev.rh.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
  Optional<Session> findByJetonHash(String jetonHash);

  List<Session> findByUserAndRevoqueLeIsNull(User user);

  List<Session> findByUserIdAndRevoqueLeIsNullAndExpireLeAfter(UUID userId, Instant now);
}
