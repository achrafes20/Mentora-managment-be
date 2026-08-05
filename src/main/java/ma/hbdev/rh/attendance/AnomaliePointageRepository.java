package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AnomaliePointageRepository
    extends JpaRepository<AnomaliePointage, UUID>, JpaSpecificationExecutor<AnomaliePointage> {

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

  /**
   * EF-ATT-11 : vrai si l'épisode d'anomalies non résolues en cours a déjà déclenché une alerte de
   * seuil — évite de renotifier à chaque anomalie supplémentaire tant que l'épisode n'est pas
   * résolu.
   */
  boolean
      existsByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqualAndADeclencheAlerteSeuilTrue(
          UUID employeId, LocalDate depuis);
}
