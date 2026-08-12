package ma.hbdev.rh.auth;

import java.time.Instant;
import lombok.Getter;

/** Compte temporairement verrouillé après trop de tentatives échouées (EF-AUTH-03). */
@Getter
public class CompteVerrouilleException extends AuthException {

  private final Instant verrouilleJusquA;

  public CompteVerrouilleException(Instant verrouilleJusquA) {
    super("Compte temporairement verrouillé. Réessayez dans quelques minutes.");
    this.verrouilleJusquA = verrouilleJusquA;
  }
}
