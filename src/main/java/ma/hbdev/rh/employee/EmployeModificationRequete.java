package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** EF-EMP-02 — modification. Pas de département/manager ici : voir {@link TransfertRequete}. */
public record EmployeModificationRequete(
    @NotNull @Size(max = 100) String nom,
    @NotNull @Size(max = 100) String prenom,
    String email,
    String telephone,
    String poste,
    @NotNull LocalDate dateEmbauche,
    @NotNull TypeContratEmploye typeContrat,
    LocalDate dateFinContratPrevue,
    LocalDate dateFinStagePrevue) {}
