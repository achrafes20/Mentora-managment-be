package ma.hbdev.rh.administrative;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class JustificatifIntrouvableException extends RuntimeException {
  JustificatifIntrouvableException(UUID demandeId) {
    super("Aucun justificatif attache a la demande: " + demandeId);
  }
}
