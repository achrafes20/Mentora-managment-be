package ma.hbdev.rh.administrative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record PeriodeBlocageCongesRequete(
    @NotNull(message = "La date de debut est obligatoire") LocalDate dateDebut,
    @NotNull(message = "La date de fin est obligatoire") LocalDate dateFin,
    @NotBlank(message = "Le libelle est obligatoire") String libelle) {}
