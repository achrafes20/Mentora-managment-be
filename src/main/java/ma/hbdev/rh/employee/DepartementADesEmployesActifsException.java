package ma.hbdev.rh.employee;

import java.util.List;
import java.util.stream.Collectors;

/** EF-EMP-10 — désactivation bloquée tant que des employés actifs sont rattachés. */
class DepartementADesEmployesActifsException extends RuntimeException {

  DepartementADesEmployesActifsException(List<Employe> employesActifs) {
    super(
        "Département non désactivable, employés actifs à réaffecter au préalable : "
            + employesActifs.stream()
                .map(e -> e.getPrenom() + " " + e.getNom())
                .collect(Collectors.joining(", ")));
  }
}
