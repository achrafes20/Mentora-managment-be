package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion de la politique de congés par type de contrat (EF-ADM-11), extrait d'{@code
 * AdministrativeService} — CRUD Admin-only, plus {@link #tauxAcquisitionMensuel} consommé par
 * {@code AdministrativeService#acquis} pour le calcul de solde (seul point de couplage restant
 * entre les deux services, via injection normale).
 */
@Service
@Transactional
class PolitiqueCongeService {

  // EF-ADM-11 : miroir léger du type Postgres type_contrat_employe, sans dépendre du enum
  // package-private employee.TypeContratEmploye — même principe que EmployeInfo, déjà lu en
  // String brut plutôt que via une dépendance croisée de module.
  private static final Set<String> TYPES_CONTRAT_CONNUS =
      Set.of("CDI", "CDD", "STAGIAIRE", "STAGIAIRE_REMUNERE");

  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher evenements;

  PolitiqueCongeService(JdbcTemplate jdbcTemplate, ApplicationEventPublisher evenements) {
    this.jdbcTemplate = jdbcTemplate;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  List<PolitiqueCongeReponse> lister() {
    return jdbcTemplate.query(
        """
        select type_contrat::text, jours_par_mois, modifie_par, modifie_le
          from politique_conges
         order by type_contrat
        """,
        (rs, rowNum) ->
            new PolitiqueCongeReponse(
                rs.getString("type_contrat"),
                rs.getBigDecimal("jours_par_mois"),
                rs.getObject("modifie_par", UUID.class),
                // pgjdbc ne convertit pas timestamptz -> java.time.Instant directement via
                // getObject(col, Class) (seul OffsetDateTime/LocalDateTime le sont).
                rs.getObject("modifie_le", OffsetDateTime.class).toInstant()));
  }

  PolitiqueCongeReponse modifier(String typeContrat, BigDecimal joursParMois) {
    verifierAdmin();
    if (!TYPES_CONTRAT_CONNUS.contains(typeContrat)) {
      throw new IllegalArgumentException("Type de contrat inconnu : " + typeContrat);
    }
    jdbcTemplate.update(
        """
        update politique_conges
           set jours_par_mois = ?, modifie_par = ?, modifie_le = now()
         where type_contrat = cast(? as type_contrat_employe)
        """,
        joursParMois,
        utilisateurCourant(),
        typeContrat);
    evenements.publishEvent(new PolitiqueCongeModifieeEvent(typeContrat));
    return lister().stream()
        .filter(p -> p.typeContrat().equals(typeContrat))
        .findFirst()
        .orElseThrow();
  }

  @Transactional(readOnly = true)
  BigDecimal tauxAcquisitionMensuel(String typeContrat) {
    return jdbcTemplate.query(
        "select jours_par_mois from politique_conges where type_contrat = cast(? as type_contrat_employe)",
        rs -> rs.next() ? rs.getBigDecimal("jours_par_mois") : BigDecimal.ZERO,
        typeContrat);
  }

  private void verifierAdmin() {
    if (!CurrentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Action reservee admin");
    }
  }

  private UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }
}
