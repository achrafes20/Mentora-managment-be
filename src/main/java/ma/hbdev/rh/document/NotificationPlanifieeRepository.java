package ma.hbdev.rh.document;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationPlanifieeRepository extends JpaRepository<NotificationPlanifiee, UUID> {
  List<NotificationPlanifiee> findByEmployeIdAndStatutIn(UUID employeId, List<StatutNotificationPlanifiee> statuts);
  List<NotificationPlanifiee> findByStatutAndDateEcheanceLessThanEqual(StatutNotificationPlanifiee statut, LocalDate date);
  List<NotificationPlanifiee> findAllByOrderByCreeLeDesc();
  boolean existsByEmployeIdAndStatut(UUID employeId, StatutNotificationPlanifiee statut);
}
