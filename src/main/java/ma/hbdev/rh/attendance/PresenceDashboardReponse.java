package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.UUID;

/**
 * Tableau de bord Présence : indicateurs agrégés sur les 30 derniers jours calendaires complets
 * (jusqu'à hier inclus — aujourd'hui n'est pas terminé, l'inclure fausserait le taux). Périmètre
 * résolu comme partout ailleurs dans le module ({@link PointageService#exporter}) : équipe directe
 * pour un Manager, tout le monde pour un Admin.
 */
public record PresenceDashboardReponse(
    double tauxPresence30Jours,
    long joursOuvresPeriode,
    List<AnomalieEmployeReponse> topAnomaliesRecurrentes,
    List<RepartitionTypeAnomalieReponse> repartitionParType) {

  public record AnomalieEmployeReponse(UUID employeId, String nomComplet, long nombreAnomalies) {}

  public record RepartitionTypeAnomalieReponse(TypeAnomaliePointage type, long nombre) {}
}
