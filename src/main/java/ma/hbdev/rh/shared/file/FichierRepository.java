package ma.hbdev.rh.shared.file;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FichierRepository extends JpaRepository<Fichier, UUID> {}
