package ma.hbdev.rh.attendance;

import java.util.UUID;

class SiteQrCodeIntrouvableException extends RuntimeException {
  SiteQrCodeIntrouvableException(UUID id) {
    super("QR de site introuvable : " + id);
  }
}
