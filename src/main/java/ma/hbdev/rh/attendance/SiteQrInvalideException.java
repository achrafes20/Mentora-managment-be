package ma.hbdev.rh.attendance;

class SiteQrInvalideException extends RuntimeException {
  SiteQrInvalideException() {
    super("QR de site invalide ou désactivé.");
  }
}
