package ma.hbdev.rh.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnvoiDocumentRhRepository extends JpaRepository<EnvoiDocument, UUID> {
  List<EnvoiDocument> findByEmployeIdOrderByDateEnvoiDesc(UUID employeId);
}
