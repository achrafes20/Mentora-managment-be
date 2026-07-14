package ma.hbdev.rh.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Jour de la semaine associé à un planning de télétravail (EF-ATT-08). */
@Entity
@Table(name = "plannings_teletravail_jours")
class PlanningTeletravailJour {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "jour_semaine", nullable = false)
  private TypeJourSemaine jourSemaine;

  protected PlanningTeletravailJour() {}

  PlanningTeletravailJour(TypeJourSemaine jourSemaine) {
    this.jourSemaine = jourSemaine;
  }

  UUID getId() {
    return id;
  }

  TypeJourSemaine getJourSemaine() {
    return jourSemaine;
  }
}
