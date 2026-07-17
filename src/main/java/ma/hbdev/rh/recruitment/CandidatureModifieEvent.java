package ma.hbdev.rh.recruitment;

import java.util.UUID;

/**
 * Publié à chaque ingestion/changement de statut/relance d'analyse — consommé par l'écouteur
 * d'audit (T3.A1).
 */
public record CandidatureModifieEvent(UUID candidatureId, String action) {}
