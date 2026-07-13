package ma.hbdev.rh.shared.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationParametreRepository
    extends JpaRepository<ConfigurationParametre, UUID> {
  Optional<ConfigurationParametre> findByCle(String cle);
}
