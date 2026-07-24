package ma.hbdev.rh.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NFR-SEC-08 : complexité de mot de passe imposée à la création et à la modification. Constante de
 * sécurité fixée au déploiement (`application.yml`, surchargeable par variable d'environnement) —
 * jamais admin-éditable en libre-service (cf. décision T4.B2 du 2026-07-24, `avancement-projet.md`
 * : retiré de l'ancien écran générique `configuration_parametres`, ce n'était pas une politique RH
 * évolutive).
 */
@Component
public class PasswordPolicy {

  private final int longueurMin;
  private final boolean exigeMajuscule;
  private final boolean exigeMinuscule;
  private final boolean exigeChiffre;

  public PasswordPolicy(
      @Value("${app.security.password-policy.min-length:10}") int longueurMin,
      @Value("${app.security.password-policy.require-uppercase:true}") boolean exigeMajuscule,
      @Value("${app.security.password-policy.require-lowercase:true}") boolean exigeMinuscule,
      @Value("${app.security.password-policy.require-digit:true}") boolean exigeChiffre) {
    this.longueurMin = longueurMin;
    this.exigeMajuscule = exigeMajuscule;
    this.exigeMinuscule = exigeMinuscule;
    this.exigeChiffre = exigeChiffre;
  }

  public boolean valide(String motDePasse) {
    if (motDePasse == null || motDePasse.length() < longueurMin) {
      return false;
    }
    if (exigeMajuscule && !motDePasse.matches(".*[A-Z].*")) {
      return false;
    }
    if (exigeMinuscule && !motDePasse.matches(".*[a-z].*")) {
      return false;
    }
    return !exigeChiffre || motDePasse.matches(".*[0-9].*");
  }
}
