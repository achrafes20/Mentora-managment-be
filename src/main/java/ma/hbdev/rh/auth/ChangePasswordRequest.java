package ma.hbdev.rh.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "Le mot de passe actuel est obligatoire") String motDePasseActuel,
    @NotBlank(message = "Le nouveau mot de passe est obligatoire") String nouveauMotDePasse) {}
