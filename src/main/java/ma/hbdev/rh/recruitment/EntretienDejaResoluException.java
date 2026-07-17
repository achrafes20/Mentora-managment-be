package ma.hbdev.rh.recruitment;

import java.util.UUID;

/** EF-REC-09 : reprogrammation refusée — le Manager a déjà rendu son résultat. */
class EntretienDejaResoluException extends RuntimeException {

  EntretienDejaResoluException(UUID candidatureId) {
    super("Un résultat a déjà été rendu pour l'entretien de la candidature : " + candidatureId);
  }
}
