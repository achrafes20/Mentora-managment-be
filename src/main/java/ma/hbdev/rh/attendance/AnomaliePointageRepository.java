package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnomaliePointageRepository extends JpaRepository<AnomaliePointage, UUID> {

  Page<AnomaliePointage> findAllByOrderByCreeLeDesc(Pageable pageable);

  Page<AnomaliePointage> findByResolueOrderByCreeLeDesc(boolean resolue, Pageable pageable);

  /**
   * Vérifie si une anomalie existe déjà pour cet employé à cette date (évite les doublons lors du
   * job nocturne).
   */
  boolean existsByEmployeIdAndDatePointageAndTypeAnomalie(
      UUID employeId, LocalDate datePointage, TypeAnomaliePointage typeAnomalie);

  List<AnomaliePointage> findByEmployeIdOrderByCreeLeDesc(UUID employeId);

  /** EF-ATT-11 : compte des anomalies non résolues d'un employé depuis une date donnée. */
  long countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
      UUID employeId, LocalDate depuis);
}
