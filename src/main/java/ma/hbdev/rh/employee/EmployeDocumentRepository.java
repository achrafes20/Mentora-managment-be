package ma.hbdev.rh.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeDocumentRepository extends JpaRepository<EmployeDocument, UUID> {

  List<EmployeDocument> findByEmployeIdOrderByCreeLeDesc(UUID employeId);
}
