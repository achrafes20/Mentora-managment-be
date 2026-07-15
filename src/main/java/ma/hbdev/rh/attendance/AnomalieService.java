package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur de détection des anomalies de pointage (EF-ATT-04). Les anomalies "retard" et "départ
 * anticipé" sont détectées en temps réel dans {@link PointageService}. Les anomalies "absence de
 * checkout" et "présence incomplète" sont générées chaque nuit à 01h00 via le job planifié {@link
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

  AnomalieService(
      AnomaliePointageRepository anomalieRepository,
      PointageRepository pointageRepository,
      HoraireReferenceService horaireReferenceService,
      PlanningTeletravailRepository planningRepository,
      PointageService pointageService) {
    this.anomalieRepository = anomalieRepository;
    this.pointageRepository = pointageRepository;
    this.horaireReferenceService = horaireReferenceService;
    this.planningRepository = planningRepository;
    this.pointageService = pointageService;
  }

  /** Job nocturne : détecte les anomalies de la journée précédente (01h00 chaque nuit). */
  @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Casablanca")
  public void detecterAnomaliesNocturnes() {
    LocalDate hier = LocalDate.now(ZONE).minusDays(1);
    LOG.info("Détection anomalies nocturnes pour {}", hier);

    java.time.Instant debut = hier.atStartOfDay(ZONE).toInstant();
    java.time.Instant fin = hier.plusDays(1).atStartOfDay(ZONE).toInstant();

    // Récupère tous les employés ayant eu au moins un pointage hier
    List<UUID> employesAvecPointage =
        pointageRepository.findEmployeIdsAvecPointageEntre(debut, fin);

    HoraireReference horaire = horaireReferenceService.trouverEnVigueurA(hier);
    if (horaire == null) {
      LOG.warn(
          "Aucun horaire de référence en vigueur pour {} — anomalies nocturnes ignorées", hier);
      return;
    }

    for (UUID employeId : employesAvecPointage) {
      analyserJourPourEmploye(employeId, hier, horaire);
    }
  }

  private void analyserJourPourEmploye(UUID employeId, LocalDate date, HoraireReference horaire) {

    // Court-circuit télétravail (EF-ATT-09)
    TypeJourSemaine jourSemaine = TypeJourSemaine.valueOf(date.getDayOfWeek().name().toLowerCase());
    if (planningRepository.estEnTeletravail(employeId, date, jourSemaine)) {
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

    if (aEntree && !aSortie) {
      // Entré mais pas sorti → absence_checkout
      pointageService.enregistrerAnomalieIdempotent(
          employeId, date, TypeAnomaliePointage.absence_checkout, entreeId, null);
    } else if (!aEntree && !aSortie) {
      // Aucun scan → présence incomplète (absent sans justification)
      pointageService.enregistrerAnomalieIdempotent(
          employeId, date, TypeAnomaliePointage.presence_incomplete, null, null);
    }
  }

  @Transactional(readOnly = true)
  public Page<AnomaliePointage> lister(Boolean resolue, Pageable pageable) {
    if (resolue != null) {
      return anomalieRepository.findByResolueOrderByCreeLeDesc(resolue, pageable);
    }
    return anomalieRepository.findAllByOrderByCreeLeDesc(pageable);
  }

  public AnomaliePointage marquerResolue(UUID id) {
    AnomaliePointage anomalie =
        anomalieRepository.findById(id).orElseThrow(() -> new AnomalieIntrouvableException(id));
    anomalie.marquerResolue();
    return anomalie;
  }
}
