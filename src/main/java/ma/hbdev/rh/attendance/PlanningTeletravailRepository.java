package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlanningTeletravailRepository extends JpaRepository<PlanningTeletravail, UUID> {

  @Query(
      """
      SELECT pt FROM PlanningTeletravail pt
      LEFT JOIN FETCH pt.jours
      WHERE pt.employeId = :employeId
      ORDER BY pt.dateDebut DESC
      """)
  List<PlanningTeletravail> findByEmployeIdOrderByDateDebutDesc(@Param("employeId") UUID employeId);

  /**
   * Requête de référence (telework-schema-addition.sql) : "cet employé est-il en télétravail à
   * cette date ?" — utilisée par le moteur d'anomalies pour court-circuiter EF-ATT-04 (EF-ATT-09).
   */
  @Query(
      """
      SELECT COUNT(pt) > 0 FROM PlanningTeletravail pt
      JOIN pt.jours ptj
      WHERE pt.employeId = :employeId
        AND :date BETWEEN pt.dateDebut AND COALESCE(pt.dateFin, :date)
        AND ptj.jourSemaine = :jourSemaine
      """)
  boolean estEnTeletravail(
      @Param("employeId") UUID employeId,
      @Param("date") LocalDate date,
      @Param("jourSemaine") TypeJourSemaine jourSemaine);

  /**
   * Surcharge pratique : dérive le jour de semaine depuis la date, pour les deux seuls appelants
   * (détection temps réel dans {@code PointageService}, job nocturne dans {@code AnomalieService})
   * — un seul endroit qui sait comment déduire {@link TypeJourSemaine} d'une {@link LocalDate},
   * pour que les deux ne puissent pas diverger.
   */
  default boolean estEnTeletravail(UUID employeId, LocalDate date) {
    return estEnTeletravail(employeId, date, TypeJourSemaine.depuis(date.getDayOfWeek()));
  }
}
