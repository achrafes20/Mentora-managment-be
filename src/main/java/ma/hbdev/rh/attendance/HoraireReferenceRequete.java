package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** Requête de création/modification d'un horaire de référence (EF-ATT-07). */
public record HoraireReferenceRequete(
    @NotNull LocalTime heureDebutMatin,
    @NotNull LocalTime heureFinMatin,
    @NotNull LocalTime heureDebutApresMidi,
    @NotNull LocalTime heureFinApresMidi,
    int toleranceMinutes,
    @NotNull LocalDate dateEffet) {}
