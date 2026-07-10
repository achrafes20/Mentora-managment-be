package ma.hbdev.rh.employee;

import java.util.UUID;

/**
 * Publié à chaque création/modification/transfert/désactivation — consommé par l'écouteur d'audit
 * (T3.A1).
 */
public record EmployeModifieEvent(UUID employeId, String action) {}
