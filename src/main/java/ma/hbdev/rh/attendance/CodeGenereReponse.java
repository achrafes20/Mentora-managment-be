package ma.hbdev.rh.attendance;

import java.util.UUID;

/**
 * Code d'activation en clair — affiché une seule fois à l'Admin/délégué qui vient de le générer.
 */
public record CodeGenereReponse(UUID id, String code) {
  static CodeGenereReponse depuis(KiosqueActivationService.CodeGenere genere) {
    return new CodeGenereReponse(genere.id(), genere.code());
  }
}
