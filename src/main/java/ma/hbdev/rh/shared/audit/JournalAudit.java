package ma.hbdev.rh.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
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
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "journal_audit")
class JournalAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "utilisateur_id")
  private UUID utilisateurId;

  @Column(nullable = false, length = 150)
  private String action;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private ModuleAudit module;

  @Column(name = "entite_type", length = 100)
  private String entiteType;

  @Column(name = "entite_id")
  private UUID entiteId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode details;

  @Column(name = "en_delegation", nullable = false)
  private boolean enDelegation;

  @Column(name = "delegation_id")
  private UUID delegationId;

  @Column(nullable = false, insertable = false, updatable = false)
  private Instant horodatage;

  protected JournalAudit() {}

  /**
   * @param delegationId EF-AUTH-14 : id de la délégation active sous laquelle l'action a été
   *     réalisée, ou {@code null} si l'auteur agissait avec ses propres droits.
   */
  JournalAudit(EvenementMetier evenement, UUID utilisateurId, JsonNode details, UUID delegationId) {
    this.utilisateurId = utilisateurId;
    this.action = evenement.action();
    this.module = evenement.module();
    this.entiteType = evenement.entiteType();
    this.entiteId = evenement.entiteId();
    this.details = details;
    this.delegationId = delegationId;
    this.enDelegation = delegationId != null;
  }
}
