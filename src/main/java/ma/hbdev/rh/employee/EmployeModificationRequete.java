package ma.hbdev.rh.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** EF-EMP-02 — modification. Pas de département/manager ici : voir {@link TransfertRequete}. */
public record EmployeModificationRequete(
    @NotBlank @Size(max = 100) String nom,
    @NotBlank @Size(max = 100) String prenom,
    @Email String email,
    String telephone,
    String poste,
    @NotNull LocalDate dateEmbauche,
    @NotNull TypeContratEmploye typeContrat,
    LocalDate dateFinContratPrevue,
    LocalDate dateFinStagePrevue,
    SexeEmploye sexe,
    String cin,
    String sujetStage) {}
