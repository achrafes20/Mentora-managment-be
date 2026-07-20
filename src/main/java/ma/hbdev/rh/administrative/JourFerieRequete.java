package ma.hbdev.rh.administrative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record JourFerieRequete(
    @NotNull(message = "La date est obligatoire") LocalDate dateFerie,
    @NotBlank(message = "Le libelle est obligatoire") String libelle) {}
