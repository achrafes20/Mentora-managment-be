package ma.hbdev.rh.recruitment;

import java.time.Instant;
import java.util.UUID;

/**
 * EF-REC-09 : reprogrammation d'un entretien pas encore résolu — les deux champs sont optionnels
 * (seul un changement de manager, ou seulement de date, ou les deux) ; au moins un doit être fourni
 * côté frontend, sinon l'appel est un no-op silencieux.
 */
public record ReprogrammerEntretienRequete(UUID managerId, Instant dateEntretien) {}
