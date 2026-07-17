package ma.hbdev.rh.recruitment;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * @param managerId requis uniquement pour la transition vers "entretien" (EF-REC-08) — résolu côté
 *     frontend depuis {@code GET /api/departements} (le champ {@code managerId} y est déjà public),
 *     pas de nouvel appel cross-module nécessaire côté backend.
 * @param dateEntretien date/heure planifiée de l'entretien, requise pour la transition vers
 *     "entretien" — modifiable ensuite via {@code POST .../entretien/reprogrammer}.
 * @param corpsMessage optionnel, utilisé uniquement pour la transition vers "rejete" (EF-REC-14) —
 *     un corps par défaut est utilisé si absent.
 */
public record ChangerStatutRequete(
    @NotNull StatutCandidature statut,
    UUID managerId,
    Instant dateEntretien,
    String corpsMessage) {}
