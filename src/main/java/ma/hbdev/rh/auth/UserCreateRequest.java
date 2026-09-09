package ma.hbdev.rh.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * EF-EMP-18 : un Manager est aussi un employé — departementId/poste/typeContrat/dateEmbauche ne
 * sont pas annotés {@code @NotNull} ici (un Admin n'en a pas besoin), mais deviennent obligatoires
 * pour {@code role == manager} — validé dans {@code UserService#create}, pas ici, car la règle
 * dépend d'un autre champ du même objet (hors portée de bean validation simple par champ).
 */
public record UserCreateRequest(
    @NotBlank(message = "L'adresse e-mail est obligatoire")
        @Email(message = "Format d'e-mail invalide")
        String email,
    @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse,
    @NotNull(message = "Le role est obligatoire") RoleUtilisateur role,
    @NotBlank(message = "Le nom est obligatoire") String nom,
    @NotBlank(message = "Le prenom est obligatoire") String prenom,
    @Size(max = 64, message = "L'identifiant Mattermost ne peut pas depasser 64 caracteres")
        String mattermostUserId,
    UUID departementId,
    String poste,
    String typeContrat,
    LocalDate dateEmbauche) {

  public UserCreateRequest(
      String email, String motDePasse, RoleUtilisateur role, String nom, String prenom) {
    this(email, motDePasse, role, nom, prenom, null, null, null, null, null);
  }
}
