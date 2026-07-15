package ma.hbdev.rh.employee;

import java.util.UUID;

/**
 * Publié à chaque exécution réelle (hors dry-run) d'un lot d'import — consommé par l'écouteur
 * d'audit (T3.A1).
 */
public record ImportExecuteEvent(UUID lotId, String cible) {}
