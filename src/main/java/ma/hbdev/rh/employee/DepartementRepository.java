package ma.hbdev.rh.employee;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DepartementRepository extends JpaRepository<Departement, UUID> {

  boolean existsByNomIgnoreCase(String nom);
}
