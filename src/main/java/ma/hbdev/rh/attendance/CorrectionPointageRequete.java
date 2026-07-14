package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Requête de correction manuelle d'un pointage (EF-ATT-06 — Could). */
public record CorrectionPointageRequete(@NotNull Instant nouvelHorodatage, @NotNull String motif) {}
