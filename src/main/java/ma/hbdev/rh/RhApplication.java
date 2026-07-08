package ma.hbdev.rh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application Mentora RH Backend.
 *
 * <p>Architecture : feature-based vertical slices sous ma.hbdev.rh.* — voir
 * docs/ai-instructions.md §Architecture rules.
 */
@SpringBootApplication
public class RhApplication {

  public static void main(String[] args) {
    SpringApplication.run(RhApplication.class, args);
  }
}
