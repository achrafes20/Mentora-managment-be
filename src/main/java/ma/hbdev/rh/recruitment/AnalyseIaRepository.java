package ma.hbdev.rh.recruitment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnalyseIaRepository extends JpaRepository<AnalyseIa, UUID> {

  List<AnalyseIa> findByCandidatureIdOrderByDateAnalyseDesc(UUID candidatureId);
}
