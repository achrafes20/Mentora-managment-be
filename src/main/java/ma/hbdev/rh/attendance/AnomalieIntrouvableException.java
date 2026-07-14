package ma.hbdev.rh.attendance;

import java.util.UUID;

class AnomalieIntrouvableException extends RuntimeException {
  AnomalieIntrouvableException(UUID id) {
    super("Anomalie introuvable : " + id);
  }
}
