package ma.hbdev.rh.recruitment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EnvoiDocumentRepository extends JpaRepository<EnvoiDocument, UUID> {}
