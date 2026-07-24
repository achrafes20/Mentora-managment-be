package ma.hbdev.rh.attendance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PolitiqueAnomaliesRepository extends JpaRepository<PolitiqueAnomalies, UUID> {}
