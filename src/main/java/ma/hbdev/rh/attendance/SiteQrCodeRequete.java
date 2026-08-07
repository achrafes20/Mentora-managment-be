package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SiteQrCodeRequete(@NotBlank @Size(max = 100) String libelle) {}
