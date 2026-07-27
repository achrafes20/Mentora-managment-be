package ma.hbdev.rh.document;

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

@Entity(name = "DocumentEnvoiRh")
@Table(name = "envois_documents")
class EnvoiDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "employe_id")
  private UUID employeId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_document", nullable = false)
  private TypeDocumentRh typeDocument;

  @Column(name = "fichier_id")
  private UUID fichierId;

  @Column(name = "destinataire_email", nullable = false)
  private String destinataireEmail;

  @Column(name = "corps_message", columnDefinition = "TEXT")
  private String corpsMessage;

  @Column(name = "envoye_par")
  private UUID envoyePar;

  @Column(name = "date_envoi", insertable = false, updatable = false)
  private Instant dateEnvoi;

  protected EnvoiDocument() {}

  EnvoiDocument(
      UUID employeId,
      TypeDocumentRh typeDocument,
      UUID fichierId,
      String destinataireEmail,
      String corpsMessage,
      UUID envoyePar) {
    this.employeId = employeId;
    this.typeDocument = typeDocument;
    this.fichierId = fichierId;
    this.destinataireEmail = destinataireEmail;
    this.corpsMessage = corpsMessage;
    this.envoyePar = envoyePar;
  }

  UUID getId() {
    return id;
  }

  UUID getEmployeId() {
    return employeId;
  }

  TypeDocumentRh getTypeDocument() {
    return typeDocument;
  }

  UUID getFichierId() {
    return fichierId;
  }

  String getDestinataireEmail() {
    return destinataireEmail;
  }

  String getCorpsMessage() {
    return corpsMessage;
  }

  UUID getEnvoyePar() {
    return envoyePar;
  }

  Instant getDateEnvoi() {
    return dateEnvoi;
  }
}
