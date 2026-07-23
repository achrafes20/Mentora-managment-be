package ma.hbdev.rh.auth;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record DelegationCreationRequete(
    @NotNull UUID delegueId, @NotNull LocalDate dateDebut, @NotNull LocalDate dateFin) {}
