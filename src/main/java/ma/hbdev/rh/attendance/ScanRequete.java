package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Requête de scan kiosque (EF-ATT-02). */
public record ScanRequete(@NotBlank String valeurQr, @NotNull TypeScanPointage typeScan) {}
