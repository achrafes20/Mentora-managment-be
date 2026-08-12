package ma.hbdev.rh.document;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationPlanifieeRepository extends JpaRepository<NotificationPlanifiee, UUID> {
  List<NotificationPlanifiee> findByEmployeIdAndStatutIn(
      UUID employeId, List<StatutNotificationPlanifiee> statuts);

  List<NotificationPlanifiee> findByStatutAndDateEcheanceLessThanEqual(
      StatutNotificationPlanifiee statut, LocalDate date);

  // Variante paginée pour l'écran Documents RH (EF-DOC) : la fenêtre de 30 jours peut regrouper
  // beaucoup de stagiaires en été, cf. findByStatutAndDateEcheanceLessThanEqual ci-dessus qui
  // reste utilisée telle quelle par le balayage interne (executerSurveillance), non paginé.
  Page<NotificationPlanifiee> findByStatutAndDateEcheanceLessThanEqual(
      StatutNotificationPlanifiee statut, LocalDate date, Pageable pageable);

  List<NotificationPlanifiee> findAllByOrderByCreeLeDesc();

  boolean existsByEmployeIdAndStatut(UUID employeId, StatutNotificationPlanifiee statut);
}
