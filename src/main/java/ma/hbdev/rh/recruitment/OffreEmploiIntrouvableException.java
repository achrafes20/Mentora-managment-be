package ma.hbdev.rh.recruitment;

import java.util.UUID;

class OffreEmploiIntrouvableException extends RuntimeException {

  OffreEmploiIntrouvableException(UUID id) {
    super("Offre d'emploi introuvable : " + id);
  }
}
