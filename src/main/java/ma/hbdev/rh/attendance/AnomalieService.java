package ma.hbdev.rh.attendance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur de détection des anomalies de pointage (EF-ATT-04). Les anomalies "retard" et "départ
 * anticipé" sont détectées en temps réel dans {@link PointageService}. L'anomalie "absence de
 * checkout" est générée chaque nuit à 01h00 via le job planifié {@link
 * #detecterAnomaliesNocturnes()} qui examine la journée précédente pour tous les employés ayant
 * pointé.
 *
 * <p>Le court-circuit télétravail (EF-ATT-09) est appliqué dans les deux cas : aucune anomalie
 * n'est générée si l'employé était en télétravail ce jour-là.
 */
@Service
@Transactional
public class AnomalieService {

  private static final Logger LOG = LoggerFactory.getLogger(AnomalieService.class);
  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

  private final AnomaliePointageRepository anomalieRepository;
  private final PointageRepository pointageRepository;
  private final HoraireReferenceService horaireReferenceService;
  private final PlanningTeletravailRepository planningRepository;
  private final PointageService pointageService;
  private final JdbcTemplate jdbcTemplate;

  AnomalieService(
      AnomaliePointageRepository anomalieRepository,
      PointageRepository pointageRepository,
      HoraireReferenceService horaireReferenceService,
      PlanningTeletravailRepository planningRepository,
      PointageService pointageService,
      JdbcTemplate jdbcTemplate) {
    this.anomalieRepository = anomalieRepository;
    this.pointageRepository = pointageRepository;
    this.horaireReferenceService = horaireReferenceService;
    this.planningRepository = planningRepository;
    this.pointageService = pointageService;
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Job nocturne : détecte les anomalies de la journée précédente (01h00 chaque nuit). */
  @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Casablanca")
  public void detecterAnomaliesNocturnes() {
    detecterAnomaliesPourJournee(LocalDate.now(ZONE).minusDays(1));
  }

  /**
   * Cœur de la détection nocturne, séparé du déclencheur cron ci-dessus pour rester testable sur
   * une date choisie plutôt que dépendre de l'horloge réelle (utile pour cibler un jour ouvré
   * précis en test, sans attendre ou mocker {@code LocalDate.now()}).
   */
  void detecterAnomaliesPourJournee(LocalDate jour) {
    // EF-ATT-04 : jour non ouvré (week-end ou jour férié déclaré) — l'entreprise étant fermée,
    // l'absence de scan ce jour-là n'a rien d'anormal. Explicite plutôt que de compter sur
    // employesAvecPointage vide (personne ne scanne un jour fermé) : plus clair et robuste si ce
    // comportement change un jour.
    if (jour.getDayOfWeek() == DayOfWeek.SATURDAY
        || jour.getDayOfWeek() == DayOfWeek.SUNDAY
        || pointageService.estJourFerie(jour)) {
      LOG.info("Jour non ouvré ({}), détection d'anomalies nocturnes ignorée", jour);
      return;
    }

    LOG.info("Détection anomalies nocturnes pour {}", jour);

    java.time.Instant debut = jour.atStartOfDay(ZONE).toInstant();
    java.time.Instant fin = jour.plusDays(1).atStartOfDay(ZONE).toInstant();

    // Récupère tous les employés ayant eu au moins un pointage ce jour-là
    List<UUID> employesAvecPointage =
        pointageRepository.findEmployeIdsAvecPointageEntre(debut, fin);

    HoraireReference horaire = horaireReferenceService.trouverEnVigueurA(jour);
    if (horaire == null) {
      LOG.warn(
          "Aucun horaire de référence en vigueur pour {} — anomalies nocturnes ignorées", jour);
      return;
    }

    for (UUID employeId : employesAvecPointage) {
      analyserJourPourEmploye(employeId, jour, horaire);
    }

    // EF-ATT-04 : absence totale — un employé actif ce jour-là (déjà embauché) sans aucun
    // pointage. Complémentaire de la boucle ci-dessus, qui ne balaie que les employés ayant
    // pointé au moins une fois : sans ça, une absence complète et injustifiée ne déclenchait
    // jusqu'ici aucune alerte.
    Set<UUID> avecPointage = new HashSet<>(employesAvecPointage);
    List<UUID> employesActifs =
        jdbcTemplate.queryForList(
            "select id from employes where statut = 'actif' and date_embauche <= ?",
            UUID.class,
            jour);
    for (UUID employeId : employesActifs) {
      if (!avecPointage.contains(employeId)) {
        analyserAbsenceTotale(employeId, jour);
      }
    }
  }

  private void analyserAbsenceTotale(UUID employeId, LocalDate date) {
    // Court-circuits déjà établis pour les autres anomalies : télétravail (EF-ATT-09) et,
    // nouveau ici, congé approuvé — une absence couverte par un congé n'a rien d'anormal.
    if (planningRepository.estEnTeletravail(employeId, date)) {
      return;
    }
    if (enCongeApprouve(employeId, date)) {
      return;
    }
    pointageService.enregistrerAnomalieIdempotent(
        employeId, date, TypeAnomaliePointage.absence_totale, null, null);
  }

  // Lecture brute dans la table de administrative (pas d'appel à son service/repository) — même
  // principe déjà établi côté PointageService#congesApprouves pour l'export.
  private boolean enCongeApprouve(UUID employeId, LocalDate date) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*) from demandes_administratives
            where employe_id = ? and type_demande = 'conge' and statut = 'approuvee'
              and date_debut <= ? and date_fin >= ?
            """,
            Integer.class,
            employeId,
            date,
            date);
    return count != null && count > 0;
  }

  private void analyserJourPourEmploye(UUID employeId, LocalDate date, HoraireReference horaire) {

    // Court-circuit télétravail (EF-ATT-09)
    if (planningRepository.estEnTeletravail(employeId, date)) {
      return;
    }

    java.time.Instant debut = date.atStartOfDay(ZONE).toInstant();
    java.time.Instant fin = date.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<Pointage> pointages = pointageRepository.findByEmployeIdAndJour(employeId, debut, fin);

    boolean aEntree = pointages.stream().anyMatch(p -> p.getTypeScan() == TypeScanPointage.entree);
    boolean aSortie = pointages.stream().anyMatch(p -> p.getTypeScan() == TypeScanPointage.sortie);

    UUID entreeId =
        pointages.stream()
            .filter(p -> p.getTypeScan() == TypeScanPointage.entree)
            .map(Pointage::getId)
            .findFirst()
            .orElse(null);

    // employeId vient de findEmployeIdsAvecPointageEntre (au moins un pointage ce jour-là) : le
    // cas "ni entrée ni sortie" n'est donc pas atteignable ici — seul aEntree && !aSortie reste
    // possible (ex-anomalie "presence_incomplete" retirée, cf. V23, car jamais générée en
    // pratique).
    if (aEntree && !aSortie) {
      pointageService.enregistrerAnomalieIdempotent(
          employeId, date, TypeAnomaliePointage.absence_checkout, entreeId, null);
    }
  }

  /**
   * EF-ATT-04/05 : liste filtrable (employé, type, résolue, période), triée par défaut du plus
   * récent au plus ancien. EF-AUTH-03 : un Manager n'y voit que son équipe — même restriction que
   * {@code PointageService#lister}, jusqu'ici absente ici alors que l'export l'appliquait déjà.
   */
  @Transactional(readOnly = true)
  public Page<AnomaliePointage> lister(
      UUID employeId,
      TypeAnomaliePointage type,
      Boolean resolue,
      LocalDate debut,
      LocalDate fin,
      Pageable pageable) {
    Pageable pageableTrie =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            pageable.getSortOr(Sort.by(Sort.Direction.DESC, "creeLe")));
    List<UUID> employeIds =
        CurrentUser.hasRole("MANAGER")
            ? pointageService.employesDansPerimetre(employeId)
            : (employeId != null ? List.of(employeId) : null);
    return anomalieRepository.findAll(
        AnomalieSpecifications.filtrer(employeIds, type, resolue, debut, fin), pageableTrie);
  }

  public AnomaliePointage marquerResolue(UUID id) {
    AnomaliePointage anomalie =
        anomalieRepository.findById(id).orElseThrow(() -> new AnomalieIntrouvableException(id));
    anomalie.marquerResolue();
    return anomalie;
  }
}
