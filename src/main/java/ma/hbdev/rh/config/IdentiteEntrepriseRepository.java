package ma.hbdev.rh.config;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdentiteEntrepriseRepository extends JpaRepository<IdentiteEntreprise, UUID> {}
