package ma.hbdev.rh.shared.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fichiers")
class Fichier {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "nom_original", nullable = false)
  private String nomOriginal;

  @Column(name = "chemin_stockage", nullable = false)
  private String cheminStockage;

  @Column(name = "type_mime", nullable = false)
  private String typeMime;

  @Column(name = "taille_octets", nullable = false)
  private long tailleOctets;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "statut_antivirus", nullable = false)
  private StatutAntivirusFichier statutAntivirus = StatutAntivirusFichier.en_attente;

  @Column(name = "televerse_par")
  private UUID televersePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected Fichier() {}

  Fichier(
      String nomOriginal,
      String cheminStockage,
      String typeMime,
      long tailleOctets,
      UUID televersePar) {
    this.nomOriginal = nomOriginal;
    this.cheminStockage = cheminStockage;
    this.typeMime = typeMime;
    this.tailleOctets = tailleOctets;
    this.televersePar = televersePar;
  }

  UUID getId() {
    return id;
  }

  String getNomOriginal() {
    return nomOriginal;
  }

  String getCheminStockage() {
    return cheminStockage;
  }

  String getTypeMime() {
    return typeMime;
  }

  long getTailleOctets() {
    return tailleOctets;
  }
}
