package ma.hbdev.rh.shared.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JournalAuditRepository extends JpaRepository<JournalAudit, UUID> {}
