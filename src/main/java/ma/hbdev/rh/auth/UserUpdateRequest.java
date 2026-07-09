package ma.hbdev.rh.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequest(
    @NotNull(message = "Le rôle est obligatoire") RoleUtilisateur role,
    @NotBlank(message = "Le nom est obligatoire") String nom,
    @NotBlank(message = "Le prénom est obligatoire") String prenom) {}
