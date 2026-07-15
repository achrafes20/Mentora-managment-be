package ma.hbdev.rh.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ImportLigneRepository extends JpaRepository<ImportLigne, UUID> {

  List<ImportLigne> findByLotIdOrderByNumeroLigne(UUID lotId);
}
