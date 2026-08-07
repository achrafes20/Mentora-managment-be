package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * EF-ATT-17 : l'identité vient de l'appairage de l'appareil (pas de QR employé à lire), mais {@code
 * valeurQrSite} — le QR affiché au lieu de travail — reste requis : preuve de présence physique,
 * distincte de la preuve d'identité.
 */
record ScanPersonnelRequete(@NotNull TypeScanPointage typeScan, @NotBlank String valeurQrSite) {}
