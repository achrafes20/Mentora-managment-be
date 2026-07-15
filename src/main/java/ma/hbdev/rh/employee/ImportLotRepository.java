package ma.hbdev.rh.employee;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportLotRepository extends JpaRepository<ImportLot, UUID> {

  Page<ImportLot> findAllByOrderByCreeLeDesc(Pageable pageable);
}
