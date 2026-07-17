package ma.hbdev.rh.recruitment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntretienRepository extends JpaRepository<Entretien, UUID> {

  List<Entretien> findByCandidatureIdOrderByCreeLeDesc(UUID candidatureId);
}
