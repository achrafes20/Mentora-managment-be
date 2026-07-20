package ma.hbdev.rh.administrative;

import java.time.LocalDate;
import java.util.UUID;

record EmployeInfo(
    UUID id,
    String nom,
    String prenom,
    UUID managerId,
    LocalDate dateEmbauche,
    String typeContrat,
    String statut) {
  String nomComplet() {
    return (prenom == null ? "" : prenom + " ") + nom;
  }
}
