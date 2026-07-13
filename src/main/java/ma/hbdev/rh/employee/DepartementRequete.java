package ma.hbdev.rh.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Requête commune création/modification (EF-EMP-10 : nom, manager rattaché). */
public record DepartementRequete(@NotBlank @Size(max = 150) String nom, UUID managerId) {}
