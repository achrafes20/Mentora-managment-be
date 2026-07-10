package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** EF-EMP-11. */
public record TransfertRequete(
    @NotNull UUID nouveauDepartementId, UUID nouveauManagerId, @NotNull LocalDate dateEffet) {}
