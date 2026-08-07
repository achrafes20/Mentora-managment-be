package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.UUID;

public record SiteQrCodeReponse(
    UUID id, String libelle, String valeur, boolean actif, UUID creePar, Instant creeLe) {

  static SiteQrCodeReponse depuis(SiteQrCode s) {
    return new SiteQrCodeReponse(
        s.getId(), s.getLibelle(), s.getValeur(), s.isActif(), s.getCreePar(), s.getCreeLe());
  }
}
