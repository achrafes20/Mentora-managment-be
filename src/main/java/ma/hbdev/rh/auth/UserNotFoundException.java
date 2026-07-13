package ma.hbdev.rh.auth;

import java.util.UUID;

/** Exception levée quand un utilisateur n'est pas trouvé par son id. */
public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(UUID id) {
    super("Utilisateur introuvable : " + id);
  }
}
