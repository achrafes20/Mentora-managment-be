package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.Min;

record PolitiqueAnomaliesRequete(
    @Min(value = 1, message = "Le seuil doit etre au moins 1") int seuilAnomalies,
    @Min(value = 1, message = "La periode doit etre d'au moins 1 jour") int periodeJours) {}
