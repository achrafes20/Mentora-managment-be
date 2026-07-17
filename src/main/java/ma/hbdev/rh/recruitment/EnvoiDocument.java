package ma.hbdev.rh.recruitment;

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

/**
 * Projection locale, minimale, de la table partagée {@code envois_documents} (journal unifié des
 * envois : certificats RH, documents libres, e-mail de rejet candidature — cf. schema_v1.sql §8).
 * Le module recrutement n'écrit ici que pour EF-REC-14 ({@code email_rejet_candidature}, {@code
 * employe_id} laissé null). Le futur module {@code document} (T4.A1) aura sa propre projection de
 * la même table pour ses propres types d'envoi — pas de couplage entre les deux modules, chacun ne
 * mappe que les colonnes/lignes qui le concernent.
 */
@Entity
@Table(name = "envois_documents")
class EnvoiDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "candidature_id")
  private UUID candidatureId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type_document", nullable = false)
  private TypeDocumentRh typeDocument;

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
      UUID candidatureId,
      TypeDocumentRh typeDocument,
      String destinataireEmail,
      String corpsMessage,
      UUID envoyePar) {
    this.candidatureId = candidatureId;
    this.typeDocument = typeDocument;
    this.destinataireEmail = destinataireEmail;
    this.corpsMessage = corpsMessage;
    this.envoyePar = envoyePar;
  }

  UUID getId() {
    return id;
  }

  Instant getDateEnvoi() {
    return dateEnvoi;
  }
}
