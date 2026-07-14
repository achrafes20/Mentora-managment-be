package ma.hbdev.rh.attendance;

/** QR code invalide, inactif ou bloqué — kiosque refuse le scan. */
class QrCodeInvalideException extends RuntimeException {
  QrCodeInvalideException() {
    super("QR code invalide, inactif ou bloqué");
  }
}
