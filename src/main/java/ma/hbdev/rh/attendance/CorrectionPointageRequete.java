package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Requête de correction manuelle d'un pointage (EF-ATT-06 — Could). */
public record CorrectionPointageRequete(
    @NotNull Instant nouvelHorodatage, @NotBlank String motif) {}
