package ma.hbdev.rh.administrative;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record DemandeAdministrativeRequete(
    @NotNull UUID employeId,
    @NotNull TypeDemandeAdministrative typeDemande,
    GranulariteConge granularite,
    LocalDate dateDebut,
    LocalDate dateFin,
    LocalTime heureDepart,
    LocalTime heureRetourPrevue,
    String motif,
    UUID fichierJustificatifId) {}
