package ma.hbdev.rh.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET =
      "test_jwt_secret_must_be_at_least_64_chars_long_xxxxxxxxxxxxxxxxxxxxxxxxxx";

  private final JwtService service = new JwtService(SECRET, 60_000);

  @Test
  void genereEtExtraitLesInformationsDuJeton() {
    String token = service.generateToken("admin@hbdev.ma", "admin");

    assertThat(service.extractEmail(token)).isEqualTo("admin@hbdev.ma");
    assertThat(service.extractRole(token)).isEqualTo("admin");
    assertThat(service.isTokenExpired(token)).isFalse();
  }

  @Test
  void calculeUneExpirationCoherenteAvecLaDureeConfiguree() {
    Instant avant = Instant.now();
    String token = service.generateToken("manager@hbdev.ma", "manager");
    Instant expiration = service.extractExpiration(token);

    assertThat(expiration).isAfter(avant.plusMillis(59_000));
    assertThat(expiration).isBefore(avant.plusMillis(61_000));
  }

  @Test
  void detecteUnJetonExpire() {
    JwtService serviceExpirationImmediate = new JwtService(SECRET, -1_000);
    String token = serviceExpirationImmediate.generateToken("admin@hbdev.ma", "admin");

    assertThat(serviceExpirationImmediate.isTokenExpired(token)).isTrue();
  }

  @Test
  void considereUnJetonIllisibleCommeExpire() {
    assertThat(service.isTokenExpired("pas-un-vrai-jeton")).isTrue();
  }

  @Test
  void refuseUnJetonSigneAvecUnAutreSecret() {
    JwtService autreService =
        new JwtService(
            "autre_secret_completement_different_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", 60_000);
    String token = autreService.generateToken("admin@hbdev.ma", "admin");

    assertThatThrownBy(() -> service.extractClaims(token)).isInstanceOf(SignatureException.class);
  }

  @Test
  void hacheDeFaconDeterministeEtDistincteSelonLeContenu() {
    String hashA1 = service.hashToken("token-a");
    String hashA2 = service.hashToken("token-a");
    String hashB = service.hashToken("token-b");

    assertThat(hashA1).isEqualTo(hashA2);
    assertThat(hashA1).isNotEqualTo(hashB);
    assertThat(hashA1).hasSize(64); // SHA-256 en hexadécimal
  }
}
