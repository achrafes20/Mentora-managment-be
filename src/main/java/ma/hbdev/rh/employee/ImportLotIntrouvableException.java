package ma.hbdev.rh.employee;

import java.util.UUID;

class ImportLotIntrouvableException extends RuntimeException {

  ImportLotIntrouvableException(UUID id) {
    super("Lot d'import introuvable : " + id);
  }
}
