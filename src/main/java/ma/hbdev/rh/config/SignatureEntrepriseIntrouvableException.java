package ma.hbdev.rh.config;

class SignatureEntrepriseIntrouvableException extends RuntimeException {

  SignatureEntrepriseIntrouvableException() {
    super("Aucune signature d'entreprise televersee");
  }
}
