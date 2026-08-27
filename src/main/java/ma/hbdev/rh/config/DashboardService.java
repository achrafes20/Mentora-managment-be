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
    long employesActifs = countEmployesActifs();
    long demandesEnAttente = countDemandesEnAttente();
    long anomaliesDuJour = countAnomaliesDuJour();
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
   * EF-DASH-02 : vue Manager restreinte à son équipe.
   *
   * <p>Périmètre per-employé (employes.manager_id), même mécanisme que partout ailleurs dans l'app
   * (Présence, Demandes/congés) — pas departements.manager_id (headship du département, notion
   * distincte qui peut rester non-assignée sans que ça reflète l'équipe réelle du Manager).
   *
   * @param managerId id de l'utilisateur Manager
   * @return agrégats restreints à l'équipe de ce Manager
   */
  @Transactional(readOnly = true)
  public DashboardStatsReponse statsManager(UUID managerId) {
    long employesActifs = countEmployesActifsDeLequipe(managerId);
    long demandesEnAttente = countDemandesEnAttenteDeLequipe(managerId);
    long anomaliesDuJour = countAnomaliesDuJourDeLequipe(managerId);

    return new DashboardStatsReponse(
        employesActifs, demandesEnAttente, anomaliesDuJour, null, null, null);
  }

  // ────────────────────────────────────────────────
  // Méthodes privées — Admin scope (global)
  // ────────────────────────────────────────────────

  private long countEmployesActifs() {
    Long count =
        jdbc.queryForObject("SELECT COUNT(*) FROM employes WHERE statut = 'actif'", Long.class);
    return count != null ? count : 0L;
  }

  private long countDemandesEnAttente() {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM demandes_administratives WHERE statut = 'en_attente'",
            Long.class);
    return count != null ? count : 0L;
  }

  private long countAnomaliesDuJour() {
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
            new RepartitionDepartement(rs.getString("id"), rs.getString("nom"), rs.getLong("cnt")));
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
  // Méthodes privées — Manager scope (équipe, employes.manager_id)
  // ────────────────────────────────────────────────

  private long countEmployesActifsDeLequipe(UUID managerId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employes WHERE statut = 'actif' AND manager_id = ?::uuid",
            Long.class,
            managerId.toString());
    return count != null ? count : 0L;
  }

  private long countDemandesEnAttenteDeLequipe(UUID managerId) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM demandes_administratives da
            JOIN employes e ON e.id = da.employe_id
            WHERE da.statut = 'en_attente' AND e.manager_id = ?::uuid
            """,
            Long.class,
            managerId.toString());
    return count != null ? count : 0L;
  }

  private long countAnomaliesDuJourDeLequipe(UUID managerId) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM anomalies_pointage ap
            JOIN employes e ON e.id = ap.employe_id
            WHERE ap.resolue = false AND ap.date_pointage = ?
              AND e.manager_id = ?::uuid
            """,
            Long.class,
            LocalDate.now(),
            managerId.toString());
    return count != null ? count : 0L;
  }
}
