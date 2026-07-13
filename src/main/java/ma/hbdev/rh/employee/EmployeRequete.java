package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * EF-EMP-01 — création. Le département/manager initial se fixe ici ; tout changement ultérieur
 * passe par le transfert (EF-EMP-11).
 */
public record EmployeRequete(
    @NotNull @Size(max = 100) String nom,
    @NotNull @Size(max = 100) String prenom,
    String email,
    String telephone,
    String poste,
    @NotNull UUID departementId,
    UUID managerId,
    @NotNull LocalDate dateEmbauche,
    @NotNull TypeContratEmploye typeContrat,
    LocalDate dateFinContratPrevue) {}
