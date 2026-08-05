package ma.hbdev.rh.attendance;

import java.time.Instant;
import lombok.Getter;

/** Code d'activation kiosque erroné ou temporairement verrouillé (NFR-UX-02). */
@Getter
class CodeActivationInvalideException extends RuntimeException {

  /**
   * Non nul seulement quand le rejet est dû à un verrouillage — sert de base au décompte côté UI.
   */
  private final Instant verrouilleJusquA;

  CodeActivationInvalideException(String message) {
    this(message, null);
  }

  CodeActivationInvalideException(String message, Instant verrouilleJusquA) {
    super(message);
    this.verrouilleJusquA = verrouilleJusquA;
  }
}
