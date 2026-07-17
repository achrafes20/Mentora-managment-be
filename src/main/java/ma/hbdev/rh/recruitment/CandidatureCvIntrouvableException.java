package ma.hbdev.rh.recruitment;

import java.util.UUID;

class CandidatureCvIntrouvableException extends RuntimeException {

  CandidatureCvIntrouvableException(UUID candidatureId) {
    super("Aucun CV associé à la candidature : " + candidatureId);
  }
}
