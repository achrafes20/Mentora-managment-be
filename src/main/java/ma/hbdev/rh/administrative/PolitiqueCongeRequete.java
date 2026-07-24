package ma.hbdev.rh.administrative;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record PolitiqueCongeRequete(
    @NotNull(message = "Le taux est obligatoire")
        @DecimalMin(value = "0.0", message = "Le taux doit etre positif ou nul")
        BigDecimal joursParMois) {}
