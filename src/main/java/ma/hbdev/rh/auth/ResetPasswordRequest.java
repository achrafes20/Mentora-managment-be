package ma.hbdev.rh.auth;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
    @NotBlank(message = "Le jeton est obligatoire") String token,
    @NotBlank(message = "Le nouveau mot de passe est obligatoire") String nouveauMotDePasse) {}
