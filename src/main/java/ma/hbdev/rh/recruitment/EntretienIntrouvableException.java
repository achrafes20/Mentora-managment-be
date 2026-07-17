package ma.hbdev.rh.recruitment;

import java.util.UUID;

class EntretienIntrouvableException extends RuntimeException {

  EntretienIntrouvableException(UUID candidatureId) {
    super("Aucun entretien enregistré pour la candidature : " + candidatureId);
  }
}
