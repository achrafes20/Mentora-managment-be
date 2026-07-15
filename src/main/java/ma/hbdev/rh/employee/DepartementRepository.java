package ma.hbdev.rh.employee;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DepartementRepository extends JpaRepository<Departement, UUID> {

  boolean existsByNomIgnoreCase(String nom);

  // EF-EMP-07 : résolution du département cible par nom pendant l'import.
  Optional<Departement> findByNomIgnoreCase(String nom);

  /** EF-AUTH-03 : résout le département géré par un Manager, pour restreindre son périmètre. */
  Optional<Departement> findByManagerId(UUID managerId);
}
