package ma.hbdev.rh.recruitment;

/** EF-REC-07 : transition refusée par la machine à états du pipeline. */
class TransitionCandidatureInvalideException extends RuntimeException {

  TransitionCandidatureInvalideException(StatutCandidature actuel, StatutCandidature demande) {
    super("Transition invalide : " + actuel + " -> " + demande);
  }
}
