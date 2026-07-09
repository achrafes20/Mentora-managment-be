package ma.hbdev.rh.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "configuration_parametres")
@Getter
@Setter
public class ConfigurationParametre {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "cle", nullable = false, unique = true, length = 150)
  private String cle;

  @Column(name = "valeur", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private JsonNode valeur;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "modifie_par")
  private UUID modifiePar;

  @Column(name = "modifie_le", nullable = false)
  private Instant modifieLe = Instant.now();
}
