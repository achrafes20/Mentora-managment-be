package ma.hbdev.rh.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeTransfertRepository extends JpaRepository<EmployeTransfert, UUID> {

  List<EmployeTransfert> findByEmployeIdOrderByCreeLeDesc(UUID employeId);
}
