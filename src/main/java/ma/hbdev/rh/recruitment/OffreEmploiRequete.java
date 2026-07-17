package ma.hbdev.rh.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record OffreEmploiRequete(
    @NotBlank @Size(max = 200) String intitule,
    String description,
    @NotNull UUID departementId,
    List<String> motsClesRequis) {}
