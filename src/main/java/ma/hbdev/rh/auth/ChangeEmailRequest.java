package ma.hbdev.rh.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
    @NotBlank(message = "Le nouvel e-mail est obligatoire")
        @Email(message = "Format d'e-mail invalide")
        String nouvelEmail) {}
