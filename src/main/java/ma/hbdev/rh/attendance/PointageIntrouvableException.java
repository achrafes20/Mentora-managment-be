package ma.hbdev.rh.attendance;

import java.util.UUID;

class PointageIntrouvableException extends RuntimeException {
  PointageIntrouvableException(UUID id) {
    super("Pointage introuvable : " + id);
  }
}
