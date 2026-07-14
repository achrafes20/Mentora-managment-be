package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Requête de création d'un planning de télétravail (EF-ATT-08). */
public record PlanningTeletravailRequete(
    @NotNull LocalDate dateDebut, LocalDate dateFin, @NotNull List<TypeJourSemaine> jours) {}
