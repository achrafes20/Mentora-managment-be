package ma.hbdev.rh.attendance;

import java.util.UUID;

class KiosqueActivationIntrouvableException extends RuntimeException {
  KiosqueActivationIntrouvableException(UUID id) {
    super("Activation kiosque introuvable : " + id);
  }
}
