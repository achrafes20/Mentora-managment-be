package ma.hbdev.rh.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Format d'e-mail invalide")
        String email,
    @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse) {}
