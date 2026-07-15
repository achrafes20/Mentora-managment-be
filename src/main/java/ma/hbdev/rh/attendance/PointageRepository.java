package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PointageRepository extends JpaRepository<Pointage, UUID> {

  Page<Pointage> findAll(Pageable pageable);

  @Query(
      "SELECT p FROM Pointage p WHERE p.employeId = :employeId"
          + " AND p.horodatage >= :debut AND p.horodatage < :fin"
          + " ORDER BY p.horodatage ASC")
  List<Pointage> findByEmployeIdAndJour(
      @Param("employeId") UUID employeId, @Param("debut") Instant debut, @Param("fin") Instant fin);

  /** Dernier scan d'entrée pour un employé sans sortie correspondante (pour rejeter doublon). */
  @Query(
      "SELECT p FROM Pointage p WHERE p.employeId = :employeId AND p.typeScan = 'entree'"
          + " AND p.horodatage = (SELECT MAX(p2.horodatage) FROM Pointage p2"
          + " WHERE p2.employeId = :employeId AND p2.typeScan = 'entree')")
  Optional<Pointage> findDerniereEntree(@Param("employeId") UUID employeId);

  @Query("SELECT p FROM Pointage p WHERE p.employeId = :employeId" + " ORDER BY p.horodatage DESC")
  Page<Pointage> findByEmployeId(@Param("employeId") UUID employeId, Pageable pageable);

  @Query(
      "SELECT DISTINCT p.employeId FROM Pointage p WHERE p.horodatage >= :debut AND p.horodatage < :fin")
  List<UUID> findEmployeIdsAvecPointageEntre(
      @Param("debut") Instant debut, @Param("fin") Instant fin);
}
