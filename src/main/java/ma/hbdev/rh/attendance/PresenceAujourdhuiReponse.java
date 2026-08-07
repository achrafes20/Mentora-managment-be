package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

/** EF-ATT-15 : une ligne par employé du périmètre pour la vue "Présence aujourd'hui". */
public record PresenceAujourdhuiReponse(
    UUID employeId, String nomComplet, String statut, Instant heureEntree, Instant heureSortie) {}
