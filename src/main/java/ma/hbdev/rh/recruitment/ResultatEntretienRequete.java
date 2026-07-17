package ma.hbdev.rh.recruitment;

import jakarta.validation.constraints.NotNull;

public record ResultatEntretienRequete(@NotNull ResultatEntretien resultat, String commentaire) {}
