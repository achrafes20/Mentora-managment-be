package ma.hbdev.rh.auth;

/** Exception métier d'authentification — traduite en 401 par le GlobalExceptionHandler. */
public class AuthException extends RuntimeException {
  public AuthException(String message) {
    super(message);
  }
}
