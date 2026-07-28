package ma.hbdev.rh.config;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.config.DashboardStatsReponse.RepartitionDepartement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EF-DASH-01/02/04 : agrégations lecture seule pour le tableau de bord. Aucune table dédiée —
 * requêtes directes sur l'existant via {@link JdbcTemplate} (les repositories cibles sont
 * package-private dans leurs modules respectifs).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final JdbcTemplate jdbc;

  /**
   * EF-DASH-01 : vue Admin complète — toutes les métriques, toutes équipes confondues.
   *
   * @return agrégats rafraîchis à chaque appel (EF-DASH-04)
   */
  @Transactional(readOnly = true)
  public DashboardStatsReponse statsAdmin() {
    long employesActifs = countEmployesActifs(null);
    long demandesEnAttente = countDemandesEnAttente(null);
    long anomaliesDuJour = countAnomaliesDuJour(null);
    List<RepartitionDepartement> repartition = repartitionParDepartement();
    long candidaturesEnCours = countCandidaturesEnCours();
    long finContratDans7Jours = countFinContratDans7Jours();

    return new DashboardStatsReponse(
        employesActifs,
        demandesEnAttente,
        anomaliesDuJour,
        repartition,
        candidaturesEnCours,
        finContratDans7Jours);
  }

  /**
   * EF-DASH-02 : vue Manager restreinte à son département.
   *
   * @param managerId id de l'utilisateur Manager
   * @return agrégats restreints au département géré par ce Manager
   */
  @Transactional(readOnly = true)
  public DashboardStatsReponse statsManager(UUID managerId) {
    // Résoudre le département géré par ce Manager
    UUID departementId = resoudreDepartementManager(managerId);
    if (departementId == null) {
      // Manager sans département rattaché → compteurs à 0
      return new DashboardStatsReponse(0L, 0L, 0L, null, null, null);
    }

    long employesActifs = countEmployesActifsDansDepartement(departementId);
    long demandesEnAttente = countDemandesEnAttenteParDepartement(departementId);
    long anomaliesDuJour = countAnomaliesDuJourParDepartement(departementId);

    return new DashboardStatsReponse(
        employesActifs, demandesEnAttente, anomaliesDuJour, null, null, null);
  }

  // ────────────────────────────────────────────────
  // Méthodes privées — Admin scope (global)
  // ────────────────────────────────────────────────

  private long countEmployesActifs(UUID departementId) {
    if (departementId != null) {
      return countEmployesActifsDansDepartement(departementId);
    }
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employes WHERE statut = 'actif'",
            Long.class);
    return count != null ? count : 0L;
  }

  private long countDemandesEnAttente(UUID departementId) {
    if (departementId != null) {
      return countDemandesEnAttenteParDepartement(departementId);
    }
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM demandes_administratives WHERE statut = 'en_attente'",
            Long.class);
    return count != null ? count : 0L;
  }

  private long countAnomaliesDuJour(UUID departementId) {
    if (departementId != null) {
      return countAnomaliesDuJourParDepartement(departementId);
    }
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM anomalies_pointage WHERE resolue = false AND date_pointage = ?",
            Long.class,
            LocalDate.now());
    return count != null ? count : 0L;
  }

  private List<RepartitionDepartement> repartitionParDepartement() {
    return jdbc.query(
        """
        SELECT d.id::text, d.nom, COUNT(e.id) AS cnt
        FROM departements d
        LEFT JOIN employes e ON e.departement_id = d.id AND e.statut = 'actif'
        WHERE d.statut = 'actif'
        GROUP BY d.id, d.nom
        ORDER BY cnt DESC, d.nom
        """,
        (rs, rowNum) ->
            new RepartitionDepartement(
                rs.getString("id"), rs.getString("nom"), rs.getLong("cnt")));
  }

  private long countCandidaturesEnCours() {
    // "en cours" = pas encore archivé/rejeté/embauché/non_traité
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM candidatures
            WHERE statut NOT IN ('embauche', 'rejete', 'archivee', 'non_traite')
            """,
            Long.class);
    return count != null ? count : 0L;
  }

  private long countFinContratDans7Jours() {
    LocalDate aujourd = LocalDate.now();
    LocalDate limite = aujourd.plusDays(7);

    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT np.employe_id) FROM notifications_planifiees np
            JOIN employes e ON e.id = np.employe_id
            WHERE np.statut = 'planifiee'
              AND np.date_echeance <= ?
              AND e.statut = 'actif'
            """,
            Long.class,
            limite);

    return count != null ? count : 0L;
  }

  // ────────────────────────────────────────────────
  // Méthodes privées — Manager scope (département)
  // ────────────────────────────────────────────────

  private UUID resoudreDepartementManager(UUID managerId) {
    try {
      String idStr =
          jdbc.queryForObject(
              "SELECT id::text FROM departements WHERE manager_id = ?::uuid AND statut = 'actif'",
              String.class,
              managerId.toString());
      return idStr != null ? UUID.fromString(idStr) : null;
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return null;
    }
  }

  private long countEmployesActifsDansDepartement(UUID departementId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employes WHERE statut = 'actif' AND departement_id = ?::uuid",
            Long.class,
            departementId.toString());
    return count != null ? count : 0L;
  }

  private long countDemandesEnAttenteParDepartement(UUID departementId) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM demandes_administratives da
            JOIN employes e ON e.id = da.employe_id
            WHERE da.statut = 'en_attente' AND e.departement_id = ?::uuid
            """,
            Long.class,
            departementId.toString());
    return count != null ? count : 0L;
  }

  private long countAnomaliesDuJourParDepartement(UUID departementId) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM anomalies_pointage ap
            JOIN employes e ON e.id = ap.employe_id
            WHERE ap.resolue = false AND ap.date_pointage = ?
              AND e.departement_id = ?::uuid
            """,
            Long.class,
            LocalDate.now(),
            departementId.toString());
    return count != null ? count : 0L;
  }
}
