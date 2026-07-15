package ma.hbdev.rh.attendance;

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

/** Scan de pointage enregistré par le kiosque (EF-ATT-02). */
@Entity
@Table(name = "pointages")
class Pointage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "qr_code_id", nullable = false)
  private UUID qrCodeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_scan", nullable = false)
  private TypeScanPointage typeScan;

  @Column(nullable = false)
  private Instant horodatage;

  @Column(name = "horaire_reference_id")
  private UUID horaireReferenceId;

  @Column(name = "corrige_manuellement", nullable = false)
  private boolean corrigeManuellement = false;

  @Column(name = "corrige_par")
  private UUID corrigePar;

  @Column(name = "motif_correction")
  private String motifCorrection;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected Pointage() {}

  Pointage(
      UUID employeId,
      UUID qrCodeId,
      TypeScanPointage typeScan,
      Instant horodatage,
      UUID horaireReferenceId) {
    this.employeId = employeId;
    this.qrCodeId = qrCodeId;
    this.typeScan = typeScan;
    this.horodatage = horodatage;
    this.horaireReferenceId = horaireReferenceId;
  }

  void corrigerManuellement(Instant nouvelHorodatage, UUID corrigePar, String motif) {
    this.horodatage = nouvelHorodatage;
    this.corrigeManuellement = true;
    this.corrigePar = corrigePar;
    this.motifCorrection = motif;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  UUID getQrCodeId() {
    return qrCodeId;
  }

  TypeScanPointage getTypeScan() {
    return typeScan;
  }

  Instant getHorodatage() {
    return horodatage;
  }

  UUID getHoraireReferenceId() {
    return horaireReferenceId;
  }

  boolean isCorrigeManuellement() {
    return corrigeManuellement;
  }

  UUID getCorrigePar() {
    return corrigePar;
  }

  String getMotifCorrection() {
    return motifCorrection;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
