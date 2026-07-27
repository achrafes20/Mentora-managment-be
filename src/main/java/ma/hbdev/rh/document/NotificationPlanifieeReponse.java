package ma.hbdev.rh.document;

import java.time.LocalDate;
import java.util.UUID;

record NotificationPlanifieeReponse(
    UUID id, UUID employeId, String typeFinSurveillee, LocalDate dateEcheance, String statut) {

  static NotificationPlanifieeReponse depuis(NotificationPlanifiee n) {
    return new NotificationPlanifieeReponse(
        n.getId(),
        n.getEmployeId(),
        n.getTypeSurveillance().name(),
        n.getDateEcheance(),
        n.getStatut().name());
  }
}
