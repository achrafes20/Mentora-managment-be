package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** EF-DOC-08 — motif + date de départ effective, requis à la désactivation. */
public record DesactivationRequete(
    @NotNull MotifDepartEmploye motif, @NotNull LocalDate dateDepart) {}
