package ma.hbdev.rh.auth;

import java.util.UUID;

/** Exception levée quand une délégation n'est pas trouvée par son id. */
public class DelegationNotFoundException extends RuntimeException {
  public DelegationNotFoundException(UUID id) {
    super("Délégation introuvable : " + id);
  }
}
