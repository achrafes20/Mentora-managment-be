package ma.hbdev.rh.employee;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EF-EMP-XX : désactivation automatique quotidienne des employés dont le contrat (CDD) ou le stage
 * est arrivé à échéance — jusqu'ici, un employé restait "actif" indéfiniment après sa date de fin
 * prévue tant qu'un Admin ne le désactivait pas manuellement.
 *
 * <p>{@code zone} explicite (Africa/Casablanca), même raison que {@code
 * SurveillancePlanifieeService#balayageQuotidien} : le Maroc suspend l'heure d'été pendant le
 * Ramadan.
 */
@Component
class DesactivationAutomatiqueScheduler {

  private final EmployeService employeService;

  DesactivationAutomatiqueScheduler(EmployeService employeService) {
    this.employeService = employeService;
  }

  @Scheduled(cron = "${app.employes.desactivation-cron:0 30 2 * * *}", zone = "Africa/Casablanca")
  void balayageQuotidien() {
    employeService.desactiverContratsExpires();
  }
}
