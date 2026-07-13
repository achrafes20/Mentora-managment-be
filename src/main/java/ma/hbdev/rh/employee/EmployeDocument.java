package ma.hbdev.rh.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** EF-EMP-03 — pièce jointe de la fiche employé, référence un {@code Fichier} de shared/file. */
@Entity
@Table(name = "employe_documents")
class EmployeDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id", nullable = false)
  private UUID employeId;

  @Column(name = "fichier_id", nullable = false)
  private UUID fichierId;

  @Column(name = "type_document")
  private String typeDocument;

  @Column(name = "televerse_par")
  private UUID televersePar;

  @Column(name = "cree_le", insertable = false, updatable = false)
  private Instant creeLe;

  protected EmployeDocument() {}

  EmployeDocument(UUID employeId, UUID fichierId, String typeDocument, UUID televersePar) {
    this.employeId = employeId;
    this.fichierId = fichierId;
    this.typeDocument = typeDocument;
    this.televersePar = televersePar;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  UUID getFichierId() {
    return fichierId;
  }

  String getTypeDocument() {
    return typeDocument;
  }

  Instant getCreeLe() {
    return creeLe;
  }
}
