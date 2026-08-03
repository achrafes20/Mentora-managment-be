package ma.hbdev.rh.employee;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeReponse(
    UUID id,
    String nom,
    String prenom,
    String email,
    String telephone,
    String poste,
    UUID departementId,
    String departementNom,
    UUID managerId,
    LocalDate dateEmbauche,
    String typeContrat,
    LocalDate dateFinContratPrevue,
    LocalDate dateFinStagePrevue,
    LocalDate dateDepart,
    String motifDepart,
    String statut,
    UUID photoFichierId,
    UUID candidatureOrigineId,
    String sexe,
    String cin,
    Instant creeLe,
    Instant modifieLe) {

  static EmployeReponse depuis(Employe employe) {
    return new EmployeReponse(
        employe.getId(),
        employe.getNom(),
        employe.getPrenom(),
        employe.getEmail(),
        employe.getTelephone(),
        employe.getPoste(),
        employe.getDepartement().getId(),
        employe.getDepartement().getNom(),
        employe.getManagerId(),
        employe.getDateEmbauche(),
        employe.getTypeContrat().name(),
        employe.getDateFinContratPrevue(),
        employe.getDateFinStagePrevue(),
        employe.getDateDepart(),
        employe.getMotifDepart() == null ? null : employe.getMotifDepart().name(),
        employe.getStatut().name(),
        employe.getPhotoFichierId(),
        employe.getCandidatureOrigineId(),
        employe.getSexe() == null ? null : employe.getSexe().name(),
        employe.getCin(),
        employe.getCreeLe(),
        employe.getModifieLe());
  }
}
