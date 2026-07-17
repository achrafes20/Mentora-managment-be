package ma.hbdev.rh.recruitment;

import java.util.UUID;

/**
 * Publié à chaque création/modification/fermeture d'offre — consommé par l'écouteur d'audit
 * (T3.A1), même principe que {@code DepartementModifieEvent} / {@code EmployeModifieEvent}.
 */
public record OffreEmploiModifieEvent(UUID offreId, String action) {}
