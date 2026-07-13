package ma.hbdev.rh;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilitaire temporaire pour générer un hash bcrypt correct. Utilisé pour corriger le
 * V2__donnees_initiales.sql.
 */
public class GenerateBcryptHash {
  public static void main(String[] args) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    String password = args.length > 0 ? args[0] : "admin123";
    String hash = encoder.encode(password);
    System.out.println("=== BCRYPT HASH ===");
    System.out.println("Password : " + password);
    System.out.println("Hash     : " + hash);
    System.out.println("Valid    : " + encoder.matches(password, hash));
  }
}
