package ma.hbdev.rh.administrative;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class DemandeAdministrativeIntrouvableException extends RuntimeException {
  DemandeAdministrativeIntrouvableException(UUID id) {
    super("Demande administrative introuvable: " + id);
  }
}
