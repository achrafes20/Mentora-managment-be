package ma.hbdev.rh.attendance;

/**
 * EF-ATT-15 : statut d'un employé pour la vue "Présence aujourd'hui" — calculé en direct, jamais
 * persisté (contrairement à {@link TypeAnomaliePointage}, qui documente une anomalie sur un jour
 * déjà terminé).
 */
enum StatutPresenceJour {
  present,
  parti,
  teletravail,
  conge,
  absent
}
