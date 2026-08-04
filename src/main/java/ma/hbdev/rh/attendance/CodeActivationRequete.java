package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;

/** Requête de vérification d'un code d'activation kiosque (NFR-UX-02). */
public record CodeActivationRequete(@NotBlank String code) {}
