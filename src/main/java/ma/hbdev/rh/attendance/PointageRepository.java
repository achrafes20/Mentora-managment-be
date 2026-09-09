package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PointageRepository
    extends JpaRepository<Pointage, UUID>, JpaSpecificationExecutor<Pointage> {

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

  // Filtre Entrée/Sortie de /api/kiosque/mes-pointages (EF-ATT-17) — méthode dédiée plutôt qu'un
  // paramètre nullable sur findByEmployeId ci-dessus, qui a un autre appelant (listerParEmploye,
  // Admin/Manager) dont le contrat ne doit pas changer.
  @Query(
      "SELECT p FROM Pointage p WHERE p.employeId = :employeId AND p.typeScan = :typeScan"
          + " ORDER BY p.horodatage DESC")
  Page<Pointage> findByEmployeIdAndTypeScan(
      @Param("employeId") UUID employeId,
      @Param("typeScan") TypeScanPointage typeScan,
      Pageable pageable);

  @Query(
      "SELECT DISTINCT p.employeId FROM Pointage p WHERE p.horodatage >= :debut AND p.horodatage < :fin")
  List<UUID> findEmployeIdsAvecPointageEntre(
      @Param("debut") Instant debut, @Param("fin") Instant fin);

  /** EF-EXP-02 : tous les scans d'une équipe sur une période, en un seul aller-retour DB. */
  @Query(
      "SELECT p FROM Pointage p WHERE p.employeId IN :employeIds"
          + " AND p.horodatage >= :debut AND p.horodatage < :fin"
          + " ORDER BY p.employeId ASC, p.horodatage ASC")
  List<Pointage> findByEmployeIdInAndHorodatageBetween(
      @Param("employeIds") List<UUID> employeIds,
      @Param("debut") Instant debut,
      @Param("fin") Instant fin);
}
