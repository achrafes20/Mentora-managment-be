package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record DemandeAdministrativeReponse(
    UUID id,
    UUID employeId,
    String employeNomComplet,
    UUID managerId,
    TypeDemandeAdministrative typeDemande,
    GranulariteConge granularite,
    LocalDate dateDebut,
    LocalDate dateFin,
    LocalTime heureDepart,
    LocalTime heureRetourPrevue,
    String motif,
    UUID fichierJustificatifId,
    StatutDemandeAdministrative statut,
    BigDecimal dureeJours,
    UUID approuveRejetePar,
    Instant dateDecision,
    UUID creePar,
    Instant creeLe) {

  static DemandeAdministrativeReponse depuis(
      DemandeAdministrative demande, EmployeInfo employe, BigDecimal dureeJours) {
    return new DemandeAdministrativeReponse(
        demande.getId(),
        demande.getEmployeId(),
        employe.nomComplet(),
        employe.managerId(),
        demande.getTypeDemande(),
        demande.getGranularite(),
        demande.getDateDebut(),
        demande.getDateFin(),
        demande.getHeureDepart(),
        demande.getHeureRetourPrevue(),
        demande.getMotif(),
        demande.getFichierJustificatifId(),
        demande.getStatut(),
        dureeJours,
        demande.getApprouveRejetePar(),
        demande.getDateDecision(),
        demande.getCreePar(),
        demande.getCreeLe());
  }
}
