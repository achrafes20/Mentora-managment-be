package ma.hbdev.rh.recruitment;

class CandidatureDejaExistanteException extends RuntimeException {
  CandidatureDejaExistanteException(String email) {
    super("Une candidature existe deja pour " + email + " sur cette offre");
  }
}
