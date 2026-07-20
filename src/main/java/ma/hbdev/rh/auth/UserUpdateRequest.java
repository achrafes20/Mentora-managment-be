package ma.hbdev.rh.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotNull(message = "Le role est obligatoire") RoleUtilisateur role,
    @NotBlank(message = "Le nom est obligatoire") String nom,
    @NotBlank(message = "Le prenom est obligatoire") String prenom,
    @Size(max = 64, message = "L'identifiant Mattermost ne peut pas depasser 64 caracteres")
        String mattermostUserId) {

  public UserUpdateRequest(RoleUtilisateur role, String nom, String prenom) {
    this(role, nom, prenom, null);
  }
}
