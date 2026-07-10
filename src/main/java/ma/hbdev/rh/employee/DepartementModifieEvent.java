package ma.hbdev.rh.employee;

import java.util.UUID;

/**
 * Publié à chaque création/modification/désactivation — consommé plus tard par l'écouteur d'audit
 * (T3.A1).
 */
public record DepartementModifieEvent(UUID departementId, String action) {}
