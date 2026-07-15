package ma.hbdev.rh.employee;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MouvementCongeRepository extends JpaRepository<MouvementConge, UUID> {

  Optional<MouvementConge> findByEmployeIdAndTypeMouvement(
      UUID employeId, TypeMouvementConge typeMouvement);
}
