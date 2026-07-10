package ma.hbdev.rh.employee;

import java.util.UUID;

class EmployeIntrouvableException extends RuntimeException {

  EmployeIntrouvableException(UUID id) {
    super("Employé introuvable : " + id);
  }
}
