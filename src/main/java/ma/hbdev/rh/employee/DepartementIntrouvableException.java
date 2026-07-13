package ma.hbdev.rh.employee;

import java.util.UUID;

class DepartementIntrouvableException extends RuntimeException {

  DepartementIntrouvableException(UUID id) {
    super("Département introuvable : " + id);
  }
}
