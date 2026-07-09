package ma.hbdev.rh.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequest(
    @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Format d'e-mail invalide")
        String email,
    @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse,
    @NotNull(message = "Le rôle est obligatoire") RoleUtilisateur role,
    @NotBlank(message = "Le nom est obligatoire") String nom,
    @NotBlank(message = "Le prénom est obligatoire") String prenom) {}
