package ma.hbdev.rh.attendance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.export.TableauExportService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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

  private static final List<String> ENTETES_EXPORT_PRESENCE =
      List.of("Employé", "Date", "Jour", "Statut", "Durée");

  private final PointageRepository pointageRepository;
  private final QrCodeService qrCodeService;
  private final HoraireReferenceService horaireReferenceService;
  private final AnomaliePointageRepository anomalieRepository;
  private final PolitiqueAnomaliesRepository politiqueAnomaliesRepository;
  private final PlanningTeletravailRepository planningTeletravailRepository;
  private final ApplicationEventPublisher evenements;
  private final JdbcTemplate jdbcTemplate;
  private final TableauExportService tableauExportService;

  PointageService(
      PointageRepository pointageRepository,
      QrCodeService qrCodeService,
      HoraireReferenceService horaireReferenceService,
      AnomaliePointageRepository anomalieRepository,
      PolitiqueAnomaliesRepository politiqueAnomaliesRepository,
      PlanningTeletravailRepository planningTeletravailRepository,
      ApplicationEventPublisher evenements,
      JdbcTemplate jdbcTemplate,
      TableauExportService tableauExportService) {
    this.pointageRepository = pointageRepository;
    this.qrCodeService = qrCodeService;
    this.horaireReferenceService = horaireReferenceService;
    this.anomalieRepository = anomalieRepository;
    this.politiqueAnomaliesRepository = politiqueAnomaliesRepository;
    this.planningTeletravailRepository = planningTeletravailRepository;
    this.evenements = evenements;
    this.jdbcTemplate = jdbcTemplate;
    this.tableauExportService = tableauExportService;
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
      verifierSeuilAnomalies(employeId);
    }
  }

  /**
   * EF-ATT-11 : escalade sur récurrence — vérifiée après chaque nouvelle anomalie (pas seulement
   * détectée, réellement insérée : l'idempotence ci-dessus évite de re-vérifier pour un doublon).
   */
  private void verifierSeuilAnomalies(UUID employeId) {
    PolitiqueAnomalies politique =
        politiqueAnomaliesRepository.findAll().stream().findFirst().orElse(null);
    if (politique == null) {
      return;
    }
    LocalDate depuis = LocalDate.now(ZONE).minusDays(politique.getPeriodeJours());
    long nombreAnomalies =
        anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            employeId, depuis);
    if (nombreAnomalies != politique.getSeuilAnomalies()) {
      // Notifie une seule fois au moment où le seuil est franchi, pas à chaque anomalie
      // supplémentaire au-delà (sinon une spirale d'alertes répétées pour le même employé).
      return;
    }
    EmployeInfoPresence employe = employeInfo(employeId);
    if (employe == null) {
      return;
    }
    evenements.publishEvent(
        new SeuilAnomaliesDepasseEvent(
            employeId,
            employe.nomComplet(),
            employe.managerId(),
            (int) nombreAnomalies,
            politique.getSeuilAnomalies()));
  }

  private EmployeInfoPresence employeInfo(UUID employeId) {
    List<EmployeInfoPresence> resultats =
        jdbcTemplate.query(
            """
            select e.nom, e.prenom, d.manager_id
              from employes e
              left join departements d on d.id = e.departement_id
             where e.id = ?
            """,
            (rs, rowNum) ->
                new EmployeInfoPresence(
                    rs.getString("prenom") + " " + rs.getString("nom"),
                    (UUID) rs.getObject("manager_id")),
            employeId);
    return resultats.isEmpty() ? null : resultats.get(0);
  }

  private record EmployeInfoPresence(String nomComplet, UUID managerId) {}

  /**
   * Calcule la durée de présence effective pour un employé sur une journée (EF-ATT-03). Déduit
   * automatiquement la pause midi d'1h si entrée le matin et sortie l'après-midi.
   */
  @Transactional(readOnly = true)
  public Duration calculerDureeJour(UUID employeId, LocalDate date) {
    Instant debut = date.atStartOfDay(ZONE).toInstant();
    Instant fin = date.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<Pointage> pointages = pointageRepository.findByEmployeIdAndJour(employeId, debut, fin);
    return dureeDepuisPointages(pointages);
  }

  private static Duration dureeDepuisPointages(List<Pointage> pointages) {
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

  /**
   * EF-EXP-02 : feuille de présence sur une période, un employé ou une équipe (EF-ATT-10 —
   * télétravail distingué de l'absence réelle ; un congé approuvé est distingué de la même façon,
   * cf. {@link #congesApprouves} — pas dans le texte EF-ATT-10 d'origine, ajouté le 2026-07-27 en
   * testant l'export : un congé validé rendait "Absence" comme un vrai no-show, trompeur pour un
   * lecteur RH). Samedi et dimanche exclus (entreprise fermée le week-end), même convention que
   * {@code AdministrativeService.duree()}. Les jours fériés ne sont volontairement pas exclus ici,
   * cohérent avec le moteur d'anomalies (EF-ATT-04) qui ne les consulte pas non plus aujourd'hui.
   */
  @Transactional(readOnly = true)
  public byte[] exporter(FormatExport format, UUID employeId, LocalDate debut, LocalDate fin) {
    if (debut == null || fin == null || fin.isBefore(debut)) {
      throw new IllegalArgumentException("Periode invalide pour l'export de presence");
    }
    List<UUID> employeIds = employesDansPerimetre(employeId);
    if (employeIds.isEmpty()) {
      return tableauExportService.generer(
          format, "Feuille de présence", ENTETES_EXPORT_PRESENCE, List.of());
    }

    Instant debutInstant = debut.atStartOfDay(ZONE).toInstant();
    Instant finInstant = fin.plusDays(1).atStartOfDay(ZONE).toInstant();
    Map<UUID, List<Pointage>> pointagesParEmploye =
        pointageRepository
            .findByEmployeIdInAndHorodatageBetween(employeIds, debutInstant, finInstant)
            .stream()
            .collect(Collectors.groupingBy(Pointage::getEmployeId));

    List<List<String>> lignes = new ArrayList<>();
    for (UUID id : employeIds) {
      EmployeInfoPresence employe = employeInfo(id);
      String nomComplet = employe == null ? id.toString() : employe.nomComplet();
      List<PlanningTeletravail> plannings =
          planningTeletravailRepository.findByEmployeIdOrderByDateDebutDesc(id);
      List<Pointage> pointagesEmploye = pointagesParEmploye.getOrDefault(id, List.of());
      List<PeriodeCongeApprouve> conges = congesApprouves(id, debut, fin);
      for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
        if (jour.getDayOfWeek() == DayOfWeek.SUNDAY || jour.getDayOfWeek() == DayOfWeek.SATURDAY) {
          continue;
        }
        lignes.add(ligneExportJour(nomComplet, jour, plannings, pointagesEmploye, conges));
      }
    }
    return tableauExportService.generer(
        format, "Feuille de présence", ENTETES_EXPORT_PRESENCE, lignes);
  }

  // Lecture brute dans la table de administrative (pas d'appel à son service/repository — même
  // principe déjà établi que AdministrativeService#employe() côté inverse, cf. T4.B2) : un congé
  // approuvé prévaut sur le calcul présence/absence habituel, comme le fait déjà le télétravail.
  private List<PeriodeCongeApprouve> congesApprouves(
      UUID employeId, LocalDate debut, LocalDate fin) {
    return jdbcTemplate.query(
        """
        select date_debut, date_fin from demandes_administratives
        where employe_id = ? and type_demande = 'conge' and statut = 'approuvee'
          and date_fin >= ? and date_debut <= ?
        """,
        (rs, rowNum) ->
            new PeriodeCongeApprouve(
                rs.getObject("date_debut", LocalDate.class),
                rs.getObject("date_fin", LocalDate.class)),
        employeId,
        debut,
        fin);
  }

  private record PeriodeCongeApprouve(LocalDate debut, LocalDate fin) {}

  private List<UUID> employesDansPerimetre(UUID employeId) {
    if (CurrentUser.hasRole("MANAGER")) {
      UUID managerId =
          CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
      List<UUID> equipe =
          jdbcTemplate.queryForList(
              "select id from employes where manager_id = ?", UUID.class, managerId);
      if (employeId != null) {
        if (!equipe.contains(employeId)) {
          throw new AccessDeniedException("Employe hors du perimetre du Manager");
        }
        return List.of(employeId);
      }
      return equipe;
    }
    if (employeId != null) {
      return List.of(employeId);
    }
    return jdbcTemplate.queryForList("select id from employes where statut = 'actif'", UUID.class);
  }

  private static List<String> ligneExportJour(
      String nomComplet,
      LocalDate jour,
      List<PlanningTeletravail> plannings,
      List<Pointage> pointagesEmploye,
      List<PeriodeCongeApprouve> conges) {
    TypeJourSemaine jourSemaine = TypeJourSemaine.depuis(jour.getDayOfWeek());
    boolean enConge =
        conges.stream().anyMatch(c -> !jour.isBefore(c.debut()) && !jour.isAfter(c.fin()));
    boolean teletravail =
        plannings.stream()
            .anyMatch(
                p ->
                    !jour.isBefore(p.getDateDebut())
                        && (p.getDateFin() == null || !jour.isAfter(p.getDateFin()))
                        && p.getJours().stream().anyMatch(j -> j.getJourSemaine() == jourSemaine));

    List<Pointage> pointagesJour =
        pointagesEmploye.stream()
            .filter(p -> p.getHorodatage().atZone(ZONE).toLocalDate().equals(jour))
            .toList();
    boolean aEntree =
        pointagesJour.stream().anyMatch(p -> p.getTypeScan() == TypeScanPointage.entree);
    boolean aSortie =
        pointagesJour.stream().anyMatch(p -> p.getTypeScan() == TypeScanPointage.sortie);

    String statut;
    String duree;
    if (enConge) {
      // Un congé approuvé prévaut sur le télétravail (données invalides si les deux se
      // chevauchaient, mais un congé reste une absence justifiée avant tout).
      statut = "Congé";
      duree = "";
    } else if (teletravail) {
      statut = "Télétravail";
      duree = "";
    } else if (aEntree && aSortie) {
      statut = "Présent";
      duree = formatDuree(dureeDepuisPointages(pointagesJour));
    } else if (aEntree) {
      statut = "Anomalie (pas de sortie)";
      duree = "";
    } else {
      statut = "Absence";
      duree = "";
    }
    return List.of(nomComplet, jour.toString(), libelleJour(jourSemaine), statut, duree);
  }

  private static String libelleJour(TypeJourSemaine jourSemaine) {
    String nom = jourSemaine.name();
    return nom.substring(0, 1).toUpperCase() + nom.substring(1);
  }

  private static String formatDuree(Duration duree) {
    return duree.toHours() + "h" + String.format("%02d", duree.toMinutesPart());
  }
}
