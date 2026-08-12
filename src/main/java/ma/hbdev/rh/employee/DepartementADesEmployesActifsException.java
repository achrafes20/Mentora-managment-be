package ma.hbdev.rh.employee;

import java.util.List;

/** EF-EMP-10 — désactivation bloquée tant que des employés actifs sont rattachés. */
class DepartementADesEmployesActifsException extends RuntimeException {

  // Compte seulement, jamais les noms : un département peut compter des dizaines d'employés
  // actifs, la liste rendrait le message inexploitable (popup géant).
  DepartementADesEmployesActifsException(List<Employe> employesActifs) {
    super(
        "Département non désactivable : "
            + employesActifs.size()
            + " employé(s) actif(s) à réaffecter au préalable.");
  }
}
