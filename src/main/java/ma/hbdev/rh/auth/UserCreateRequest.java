package ma.hbdev.rh.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Format d'e-mail invalide")
        String email,
    @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse,
    @NotNull(message = "Le role est obligatoire") RoleUtilisateur role,
    @NotBlank(message = "Le nom est obligatoire") String nom,
    @NotBlank(message = "Le prenom est obligatoire") String prenom,
    @Size(max = 64, message = "L'identifiant Mattermost ne peut pas depasser 64 caracteres")
        String mattermostUserId) {

  public UserCreateRequest(
      String email, String motDePasse, RoleUtilisateur role, String nom, String prenom) {
    this(email, motDePasse, role, nom, prenom, null);
  }
}
