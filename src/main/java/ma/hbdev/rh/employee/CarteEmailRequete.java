package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotBlank;

public record CarteEmailRequete(
    @NotBlank String objet, @NotBlank String corps, String destinataire) {}
