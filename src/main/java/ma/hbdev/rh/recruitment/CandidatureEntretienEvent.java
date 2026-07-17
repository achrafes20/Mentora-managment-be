package ma.hbdev.rh.recruitment;

import java.util.UUID;

/**
 * EF-REC-08 : publié quand une candidature passe à l'étape "Entretien" — consommé plus tard par
 * l'écouteur Mattermost (T3.A1) pour notifier {@code managerId} et lui rendre la fiche accessible.
 */
public record CandidatureEntretienEvent(UUID candidatureId, UUID managerId) {}
