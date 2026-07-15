package ma.hbdev.rh.attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logique de pointage : scan kiosque (EF-ATT-02), calcul de durée (EF-ATT-03), correction manuelle
 * (EF-ATT-06 — Could), historique (EF-ATT-05).
 */
@Service
@Transactional
public class PointageService {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

  /** Durée de la pause midi, déduite systématiquement (EF-ATT-03). */
  private static final Duration PAUSE_MIDI = Duration.ofHours(1);

  private final PointageRepository pointageRepository;
  private final QrCodeService qrCodeService;
  private final HoraireReferenceService horaireReferenceService;
  private final AnomaliePointageRepository anomalieRepository;

  PointageService(
      PointageRepository pointageRepository,
      QrCodeService qrCodeService,
      HoraireReferenceService horaireReferenceService,
      AnomaliePointageRepository anomalieRepository) {
    this.pointageRepository = pointageRepository;
    this.qrCodeService = qrCodeService;
    this.horaireReferenceService = horaireReferenceService;
    this.anomalieRepository = anomalieRepository;
  }

  /**
   * Enregistre un scan kiosque (EF-ATT-02). L'horodatage est produit côté serveur. Règles :
   *
   * <ul>
   *   <li>QR inconnu/inactif/bloqué → exception
   *   <li>Deux scans d'entrée consécutifs → exception (EF-ATT-02)
   * </ul>
   */
  public Pointage scanner(ScanRequete requete) {
    QrCode qr =
        qrCodeService
            .resoudreParValeur(requete.valeurQr())
            .orElseThrow(QrCodeInvalideException::new);

    Instant maintenant = Instant.now();
    LocalDate dateAujourdHui = maintenant.atZone(ZONE).toLocalDate();

    // Deux scans d'entrée consécutifs → rejet
    if (requete.typeScan() == TypeScanPointage.entree) {
      boolean dejaEntre =
          pointageRepository.findDerniereEntree(qr.getEmployeId()).isPresent()
              && !avecSortieDepuis(
                  qr.getEmployeId(),
                  pointageRepository.findDerniereEntree(qr.getEmployeId()).get().getHorodatage());
      if (dejaEntre) {
        throw new DoubleEntreeException();
      }
    }

    HoraireReference horaire = horaireReferenceService.trouverEnVigueurA(dateAujourdHui);
    UUID horaireId = horaire != null ? horaire.getId() : null;

    Pointage pointage =
        pointageRepository.save(
            new Pointage(qr.getEmployeId(), qr.getId(), requete.typeScan(), maintenant, horaireId));

    // Détection immédiate des anomalies "retard" et "départ anticipé" si horaire disponible
    if (horaire != null) {
      detecterAnomalieImmediate(pointage, horaire);
    }

    return pointage;
  }

  private boolean avecSortieDepuis(UUID employeId, Instant depuisEntree) {
    Instant finJour =
        depuisEntree.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    List<Pointage> pointages =
        pointageRepository.findByEmployeIdAndJour(employeId, depuisEntree, finJour);
    return pointages.stream().anyMatch(p -> p.getTypeScan() == TypeScanPointage.sortie);
  }

  private void detecterAnomalieImmediate(Pointage pointage, HoraireReference horaire) {
    LocalTime heureScan = pointage.getHorodatage().atZone(ZONE).toLocalTime();

    if (pointage.getTypeScan() == TypeScanPointage.entree) {
      LocalTime limiteArrivee =
          horaire.getHeureDebutMatin().plusMinutes(horaire.getToleranceMinutes());
      if (heureScan.isAfter(limiteArrivee)) {
        enregistrerAnomalieIdempotent(
            pointage.getEmployeId(),
            pointage.getHorodatage().atZone(ZONE).toLocalDate(),
            TypeAnomaliePointage.retard,
            pointage.getId(),
            null);
      }
    } else {
      LocalTime limiteDepart =
          horaire.getHeureFinApresMidi().minusMinutes(horaire.getToleranceMinutes());
      if (heureScan.isBefore(limiteDepart)) {
        enregistrerAnomalieIdempotent(
            pointage.getEmployeId(),
            pointage.getHorodatage().atZone(ZONE).toLocalDate(),
            TypeAnomaliePointage.depart_anticipe,
            null,
            pointage.getId());
      }
    }
  }

  void enregistrerAnomalieIdempotent(
      UUID employeId,
      LocalDate date,
      TypeAnomaliePointage type,
      UUID pointageEntreeId,
      UUID pointageSortieId) {
    if (!anomalieRepository.existsByEmployeIdAndDatePointageAndTypeAnomalie(
        employeId, date, type)) {
      anomalieRepository.save(
          new AnomaliePointage(employeId, date, type, pointageEntreeId, pointageSortieId));
    }
  }

  /**
   * Calcule la durée de présence effective pour un employé sur une journée (EF-ATT-03). Déduit
   * automatiquement la pause midi d'1h si entrée le matin et sortie l'après-midi.
   */
  @Transactional(readOnly = true)
  public Duration calculerDureeJour(UUID employeId, LocalDate date) {
    Instant debut = date.atStartOfDay(ZONE).toInstant();
    Instant fin = date.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<Pointage> pointages = pointageRepository.findByEmployeIdAndJour(employeId, debut, fin);

    Instant entree = null;
    Duration total = Duration.ZERO;
    for (Pointage p : pointages) {
      if (p.getTypeScan() == TypeScanPointage.entree) {
        entree = p.getHorodatage();
      } else if (p.getTypeScan() == TypeScanPointage.sortie && entree != null) {
        Duration segment = Duration.between(entree, p.getHorodatage());
        total = total.plus(segment);
        entree = null;
      }
    }
    // Déduire la pause de 1h si la journée couvre la plage midi
    if (!total.isNegative() && !total.isZero() && total.compareTo(PAUSE_MIDI) > 0) {
      total = total.minus(PAUSE_MIDI);
    }
    return total;
  }

  @Transactional(readOnly = true)
  public Page<Pointage> lister(Pageable pageable) {
    return pointageRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Page<Pointage> listerParEmploye(UUID employeId, Pageable pageable) {
    return pointageRepository.findByEmployeId(employeId, pageable);
  }

  /** Correction manuelle EF-ATT-06 (Could). */
  public Pointage corrigerManuellement(UUID pointageId, CorrectionPointageRequete requete) {
    Pointage pointage =
        pointageRepository
            .findById(pointageId)
            .orElseThrow(() -> new PointageIntrouvableException(pointageId));
    UUID corrigePar = CurrentUser.id().orElse(null);
    pointage.corrigerManuellement(requete.nouvelHorodatage(), corrigePar, requete.motif());
    return pointage;
  }
}
