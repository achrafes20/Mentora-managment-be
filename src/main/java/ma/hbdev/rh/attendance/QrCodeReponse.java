package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

/** DTO de réponse pour un QR code. */
public record QrCodeReponse(
    UUID id, UUID employeId, String valeur, boolean actif, boolean bloque, Instant genereLe) {

  static QrCodeReponse depuis(QrCode q) {
    return new QrCodeReponse(
        q.getId(), q.getEmployeId(), q.getValeur(), q.isActif(), q.isBloque(), q.getGenereLe());
  }
}
