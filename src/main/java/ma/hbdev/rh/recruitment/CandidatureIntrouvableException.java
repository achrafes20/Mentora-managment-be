package ma.hbdev.rh.recruitment;

import java.util.UUID;

class CandidatureIntrouvableException extends RuntimeException {

  CandidatureIntrouvableException(UUID id) {
    super("Candidature introuvable : " + id);
  }
}
