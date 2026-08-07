package ma.hbdev.rh.attendance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.export.TableauExportService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  /** Repli si aucun horaire de référence n'est configuré (EF-ATT-03). */
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
    return scannerPourEmploye(qr.getEmployeId(), qr.getId(), requete.typeScan());
  }

  /**
   * EF-ATT-16 : cœur du scan, indépendant de la lecture d'un QR — utilisé par {@link
   * #scanner(ScanRequete)} (kiosque partagé, identité prouvée par le QR badge scanné) et par
   * l'appareil personnel (identité déjà prouvée par l'appairage du téléphone, cf.
   * KiosqueController#scannerPersonnel) où il n'y a pas de QR à lire.
   */
  /**
   * EF-ATT-16 : scan depuis l'appareil personnel de l'employé — l'identité est déjà prouvée par
   * l'appairage du téléphone (cf. KiosqueActivationService#employeAppareilPersonnel), donc pas de
   * QR à lire ; on rattache tout de même le pointage au QR actif de l'employé (colonne {@code
   * qr_code_id} non nullable) plutôt que d'en faire une colonne nullable pour ce seul cas.
   */
  public Pointage scannerPersonnel(UUID employeId, TypeScanPointage typeScan) {
    QrCode qr = qrCodeService.trouverActifDe(employeId).orElseThrow(QrCodeInvalideException::new);
    return scannerPourEmploye(employeId, qr.getId(), typeScan);
  }

  /**
   * EF-ATT-17 : historique perso affiché sur /pointage-mobile — rassure l'employé qu'il n'a pas
   * oublié de pointer. Identité déjà prouvée par l'appairage de l'appareil (appelant), donc pas de
   * périmètre Manager à appliquer ici (contrairement à lister()) — un employé ne voit que le sien.
   */
  @Transactional(readOnly = true)
  public List<PointageReponse> pointagesRecents(UUID employeId, int limite) {
    return pointageRepository
        .findByEmployeId(employeId, PageRequest.of(0, limite))
        .map(PointageReponse::depuis)
        .getContent();
  }

  Pointage scannerPourEmploye(UUID employeId, UUID qrCodeId, TypeScanPointage typeScan) {
    Instant maintenant = Instant.now();
    LocalDate dateAujourdHui = maintenant.atZone(ZONE).toLocalDate();

    // Deux scans d'entrée consécutifs → rejet
    if (typeScan == TypeScanPointage.entree) {
      boolean dejaEntre =
          pointageRepository.findDerniereEntree(employeId).isPresent()
              && !avecSortieDepuis(
                  employeId,
                  pointageRepository.findDerniereEntree(employeId).get().getHorodatage());
      if (dejaEntre) {
        throw new DoubleEntreeException();
      }
    }

    HoraireReference horaire = horaireReferenceService.trouverEnVigueurA(dateAujourdHui);
    UUID horaireId = horaire != null ? horaire.getId() : null;

    Pointage pointage =
        pointageRepository.save(new Pointage(employeId, qrCodeId, typeScan, maintenant, horaireId));

    // Détection immédiate des anomalies "retard" et "départ anticipé" si horaire disponible et
    // hors télétravail planifié (EF-ATT-04/09 — même court-circuit que
    // AnomalieService#analyserJourPourEmploye, requis ici aussi : un employé en télétravail qui
    // passe au bureau/scanne à distance ne doit jamais être marqué en retard sur un jour où il
    // n'est de toute façon pas censé arriver à l'horaire de référence sur site).
    if (horaire != null
        && !planningTeletravailRepository.estEnTeletravail(employeId, dateAujourdHui)) {
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
      AnomaliePointage anomalie =
          anomalieRepository.save(
              new AnomaliePointage(employeId, date, type, pointageEntreeId, pointageSortieId));
      notifierEmployeDeSaPropreAnomalie(employeId, type, date);
      verifierSeuilAnomalies(employeId, anomalie);
    }
  }

  /**
   * EF-ATT-04 : contrairement à l'escalade sur seuil ({@link #verifierSeuilAnomalies}, réservée au
   * Manager et déclenchée seulement au franchissement), l'employé lui-même est notifié à chaque
   * anomalie détectée sur son propre pointage, s'il dispose d'un compte applicatif ({@code
   * employes.utilisateur_id} nullable — dégradation silencieuse sinon, cf. {@link
   * AnomalieDetecteeEvent#notification}).
   */
  private void notifierEmployeDeSaPropreAnomalie(
      UUID employeId, TypeAnomaliePointage type, LocalDate date) {
    EmployeInfoPresence employe = employeInfo(employeId);
    if (employe == null) {
      return;
    }
    evenements.publishEvent(
        new AnomalieDetecteeEvent(
            employeId, employe.utilisateurId(), employe.nomComplet(), type, date));
  }

  /**
   * EF-ATT-11 : escalade sur récurrence — vérifiée après chaque nouvelle anomalie (pas seulement
   * détectée, réellement insérée : l'idempotence ci-dessus évite de re-vérifier pour un doublon).
   *
   * <p>Une seule alerte par "épisode" d'anomalies non résolues, portée par un drapeau sur
   * l'anomalie elle-même ({@code aDeclencheAlerteSeuil}) plutôt que par une comparaison du compte
   * courant au seuil : comparer un compte instantané (même avec {@code >=}) est fragile si le
   * compte peut sauter directement au-dessus du seuil sans jamais l'égaler pile — par exemple si un
   * Admin abaisse le seuil pendant qu'un employé a déjà des anomalies non résolues, le prochain
   * comptage dépasse alors le nouveau seuil sans qu'aucune vérification n'ait eu lieu au moment
   * exact du changement. Le drapeau persiste l'état "déjà notifié pour cet épisode" indépendamment
   * de la valeur du compte, et se réinitialise naturellement (filtre {@code resolue=false}) une
   * fois l'épisode résolu.
   */
  private void verifierSeuilAnomalies(UUID employeId, AnomaliePointage anomalieDeclenchante) {
    PolitiqueAnomalies politique =
        politiqueAnomaliesRepository.findAll().stream().findFirst().orElse(null);
    if (politique == null) {
      return;
    }
    LocalDate depuis = LocalDate.now(ZONE).minusDays(politique.getPeriodeJours());

    if (anomalieRepository
        .existsByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqualAndADeclencheAlerteSeuilTrue(
            employeId, depuis)) {
      return;
    }

    long nombreAnomalies =
        anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            employeId, depuis);
    if (nombreAnomalies < politique.getSeuilAnomalies()) {
      return;
    }
    EmployeInfoPresence employe = employeInfo(employeId);
    if (employe == null) {
      return;
    }
    anomalieDeclenchante.marquerADeclencheAlerteSeuil();
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
            select e.nom, e.prenom, e.utilisateur_id, d.manager_id
              from employes e
              left join departements d on d.id = e.departement_id
             where e.id = ?
            """,
            (rs, rowNum) ->
                new EmployeInfoPresence(
                    rs.getString("prenom") + " " + rs.getString("nom"),
                    (UUID) rs.getObject("manager_id"),
                    (UUID) rs.getObject("utilisateur_id")),
            employeId);
    return resultats.isEmpty() ? null : resultats.get(0);
  }

  private record EmployeInfoPresence(String nomComplet, UUID managerId, UUID utilisateurId) {}

  /**
   * Calcule la durée de présence effective pour un employé sur une journée (EF-ATT-03). Déduit
   * automatiquement la pause déjeuner, dont la durée vient de l'horaire de référence en vigueur ce
   * jour-là (repli sur {@link #PAUSE_MIDI} si aucun horaire n'est encore configuré).
   */
  @Transactional(readOnly = true)
  public Duration calculerDureeJour(UUID employeId, LocalDate date) {
    Instant debut = date.atStartOfDay(ZONE).toInstant();
    Instant fin = date.plusDays(1).atStartOfDay(ZONE).toInstant();
    List<Pointage> pointages = pointageRepository.findByEmployeIdAndJour(employeId, debut, fin);
    return dureeDepuisPointages(pointages, pauseMidiA(date));
  }

  /**
   * Pas de champ dédié pour la pause déjeuner : elle est déduite de l'écart entre {@code
   * heureFinMatin} et {@code heureDebutApresMidi} de l'horaire en vigueur — un champ séparé
   * pourrait se contredire avec ces deux heures (ex. 13h/14h saisis mais une pause à 30 min
   * ailleurs). Repli sur {@link #PAUSE_MIDI} si aucun horaire configuré, ou si l'écart est nul ou
   * négatif (horaire mal saisi).
   */
  private Duration pauseMidiA(LocalDate date) {
    HoraireReference horaire = horaireReferenceService.trouverEnVigueurA(date);
    if (horaire == null) {
      return PAUSE_MIDI;
    }
    Duration ecart = Duration.between(horaire.getHeureFinMatin(), horaire.getHeureDebutApresMidi());
    return ecart.isPositive() ? ecart : PAUSE_MIDI;
  }

  private static Duration dureeDepuisPointages(List<Pointage> pointages, Duration pauseMidi) {
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
    // Déduire la pause déjeuner si la journée couvre la plage midi
    if (!total.isNegative() && !total.isZero() && total.compareTo(pauseMidi) > 0) {
      total = total.minus(pauseMidi);
    }
    return total;
  }

  /**
   * EF-ATT-05 : historique filtrable (employé, type de scan, période). EF-AUTH-03 : un Manager n'y
   * voit que son équipe, comme {@link #exporter} le fait déjà — corrigé, jusqu'ici seul l'export
   * appliquait cette restriction.
   */
  @Transactional(readOnly = true)
  public Page<Pointage> lister(
      UUID employeId,
      TypeScanPointage typeScan,
      LocalDate debut,
      LocalDate fin,
      Pageable pageable) {
    List<UUID> employeIds =
        CurrentUser.hasRole("MANAGER")
            ? employesDansPerimetre(employeId)
            : (employeId != null ? List.of(employeId) : null);
    return pointageRepository.findAll(
        PointageSpecifications.filtrer(employeIds, typeScan, debut, fin), pageable);
  }

  @Transactional(readOnly = true)
  public Page<Pointage> listerParEmploye(UUID employeId, Pageable pageable) {
    return pointageRepository.findByEmployeId(employeId, pageable);
  }

  /** Correction manuelle EF-ATT-06 (Could) — journalisée dans journal_audit (NFR-SEC-03). */
  public Pointage corrigerManuellement(UUID pointageId, CorrectionPointageRequete requete) {
    Pointage pointage =
        pointageRepository
            .findById(pointageId)
            .orElseThrow(() -> new PointageIntrouvableException(pointageId));
    Instant ancienHorodatage = pointage.getHorodatage();
    UUID corrigePar = CurrentUser.id().orElse(null);
    pointage.corrigerManuellement(requete.nouvelHorodatage(), corrigePar, requete.motif());
    evenements.publishEvent(
        new PointageCorrigeEvent(
            pointageId, ancienHorodatage, requete.nouvelHorodatage(), requete.motif()));
    return pointage;
  }

  /**
   * EF-EXP-02 : feuille de présence sur une période, un employé ou une équipe (EF-ATT-10 —
   * télétravail distingué de l'absence réelle ; un congé approuvé est distingué de la même façon,
   * cf. {@link #congesApprouves} — pas dans le texte EF-ATT-10 d'origine, ajouté le 2026-07-27 en
   * testant l'export : un congé validé rendait "Absence" comme un vrai no-show, trompeur pour un
   * lecteur RH). Samedi, dimanche et jours fériés exclus (entreprise fermée), même convention que
   * {@code AdministrativeService.duree()}.
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
    Set<LocalDate> joursFeries = joursFeriesEntre(debut, fin);

    List<List<String>> lignes = new ArrayList<>();
    for (UUID id : employeIds) {
      EmployeInfoPresence employe = employeInfo(id);
      String nomComplet = employe == null ? id.toString() : employe.nomComplet();
      List<PlanningTeletravail> plannings =
          planningTeletravailRepository.findByEmployeIdOrderByDateDebutDesc(id);
      List<Pointage> pointagesEmploye = pointagesParEmploye.getOrDefault(id, List.of());
      List<PeriodeCongeApprouve> conges = congesApprouves(id, debut, fin);
      for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
        if (jour.getDayOfWeek() == DayOfWeek.SUNDAY
            || jour.getDayOfWeek() == DayOfWeek.SATURDAY
            || joursFeries.contains(jour)) {
          continue;
        }
        lignes.add(
            ligneExportJour(
                nomComplet, jour, plannings, pointagesEmploye, conges, pauseMidiA(jour)));
      }
    }
    return tableauExportService.generer(
        format, "Feuille de présence", ENTETES_EXPORT_PRESENCE, lignes);
  }

  /**
   * EF-ATT-15 : présence du jour même — un statut par employé du périmètre, calculé en direct
   * (contrairement à {@code absence_totale}, détectée seulement par le balayage nocturne sur un
   * jour déjà terminé — cf. AnomalieService). Même logique de classification que {@link
   * #ligneExportJour}, adaptée : "Absent" ici ne préjuge pas d'une anomalie (la journée n'est pas
   * terminée), juste qu'aucun pointage n'a encore été vu.
   */
  @Transactional(readOnly = true)
  public List<PresenceAujourdhuiReponse> aujourdhui() {
    LocalDate jour = LocalDate.now(ZONE);
    List<UUID> employeIds = employesDansPerimetre(null);
    if (employeIds.isEmpty()) {
      return List.of();
    }
    Instant debutInstant = jour.atStartOfDay(ZONE).toInstant();
    Instant finInstant = jour.plusDays(1).atStartOfDay(ZONE).toInstant();
    Map<UUID, List<Pointage>> pointagesParEmploye =
        pointageRepository
            .findByEmployeIdInAndHorodatageBetween(employeIds, debutInstant, finInstant)
            .stream()
            .collect(Collectors.groupingBy(Pointage::getEmployeId));

    List<PresenceAujourdhuiReponse> resultat = new ArrayList<>();
    for (UUID id : employeIds) {
      EmployeInfoPresence employe = employeInfo(id);
      String nomComplet = employe == null ? id.toString() : employe.nomComplet();
      List<PlanningTeletravail> plannings =
          planningTeletravailRepository.findByEmployeIdOrderByDateDebutDesc(id);
      List<Pointage> pointagesJour = pointagesParEmploye.getOrDefault(id, List.of());
      List<PeriodeCongeApprouve> conges = congesApprouves(id, jour, jour);
      resultat.add(statutAujourdhui(id, nomComplet, jour, plannings, pointagesJour, conges));
    }
    return resultat;
  }

  private static PresenceAujourdhuiReponse statutAujourdhui(
      UUID employeId,
      String nomComplet,
      LocalDate jour,
      List<PlanningTeletravail> plannings,
      List<Pointage> pointagesJour,
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
    Pointage entree =
        pointagesJour.stream()
            .filter(p -> p.getTypeScan() == TypeScanPointage.entree)
            .findFirst()
            .orElse(null);
    Pointage sortie =
        pointagesJour.stream()
            .filter(p -> p.getTypeScan() == TypeScanPointage.sortie)
            .findFirst()
            .orElse(null);

    StatutPresenceJour statut;
    if (enConge) {
      statut = StatutPresenceJour.conge;
    } else if (teletravail) {
      statut = StatutPresenceJour.teletravail;
    } else if (entree != null && sortie != null) {
      statut = StatutPresenceJour.parti;
    } else if (entree != null) {
      statut = StatutPresenceJour.present;
    } else {
      statut = StatutPresenceJour.absent;
    }
    return new PresenceAujourdhuiReponse(
        employeId,
        nomComplet,
        statut.name(),
        entree == null ? null : entree.getHorodatage(),
        sortie == null ? null : sortie.getHorodatage());
  }

  /**
   * Tableau de bord Présence : taux de couverture sur les 30 derniers jours calendaires complets
   * (aujourd'hui exclu, pas encore terminé), employés avec le plus d'anomalies non résolues
   * récurrentes, répartition des anomalies par type. Même périmètre que {@link #exporter} (équipe
   * du Manager, tout le monde pour un Admin).
   */
  @Transactional(readOnly = true)
  public PresenceDashboardReponse tableauDeBord() {
    List<UUID> employeIds = employesDansPerimetre(null);
    LocalDate fin = LocalDate.now(ZONE).minusDays(1);
    LocalDate debut = fin.minusDays(29);

    if (employeIds.isEmpty()) {
      return new PresenceDashboardReponse(0, 0, List.of(), List.of());
    }

    long joursOuvres = joursOuvresEntre(debut, fin);
    double taux = calculerTauxCouverture(employeIds, debut, fin, joursOuvres);

    List<AnomalieLigne> anomalies =
        anomaliesEntre(debut, fin).stream()
            .filter(l -> employeIds.contains(l.employeId()))
            .toList();

    List<PresenceDashboardReponse.AnomalieEmployeReponse> topAnomaliesRecurrentes =
        anomalies.stream()
            .filter(l -> !l.resolue())
            .collect(Collectors.groupingBy(AnomalieLigne::employeId, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(5)
            .map(
                e -> {
                  EmployeInfoPresence employe = employeInfo(e.getKey());
                  String nomComplet =
                      employe == null ? e.getKey().toString() : employe.nomComplet();
                  return new PresenceDashboardReponse.AnomalieEmployeReponse(
                      e.getKey(), nomComplet, e.getValue());
                })
            .toList();

    List<PresenceDashboardReponse.RepartitionTypeAnomalieReponse> repartitionParType =
        anomalies.stream()
            .collect(Collectors.groupingBy(AnomalieLigne::type, Collectors.counting()))
            .entrySet()
            .stream()
            .map(
                e ->
                    new PresenceDashboardReponse.RepartitionTypeAnomalieReponse(
                        e.getKey(), e.getValue()))
            .toList();

    return new PresenceDashboardReponse(
        taux, joursOuvres, topAnomaliesRecurrentes, repartitionParType);
  }

  private long joursOuvresEntre(LocalDate debut, LocalDate fin) {
    Set<LocalDate> joursFeries = joursFeriesEntre(debut, fin);
    long total = 0;
    for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
      if (jour.getDayOfWeek() == DayOfWeek.SUNDAY
          || jour.getDayOfWeek() == DayOfWeek.SATURDAY
          || joursFeries.contains(jour)) {
        continue;
      }
      total++;
    }
    return total;
  }

  /**
   * Un jour est "couvert" (ni absence, ni anomalie de check-out) s'il est Présent, Télétravail ou
   * Congé — réutilise la même classification que {@link #ligneExportJour} plutôt que d'en dupliquer
   * la logique, seul le statut (index 3 de la ligne) nous intéresse ici.
   */
  private double calculerTauxCouverture(
      List<UUID> employeIds, LocalDate debut, LocalDate fin, long joursOuvres) {
    if (joursOuvres == 0) {
      return 0;
    }
    Instant debutInstant = debut.atStartOfDay(ZONE).toInstant();
    Instant finInstant = fin.plusDays(1).atStartOfDay(ZONE).toInstant();
    Map<UUID, List<Pointage>> pointagesParEmploye =
        pointageRepository
            .findByEmployeIdInAndHorodatageBetween(employeIds, debutInstant, finInstant)
            .stream()
            .collect(Collectors.groupingBy(Pointage::getEmployeId));
    Set<LocalDate> joursFeries = joursFeriesEntre(debut, fin);

    long joursCouverts = 0;
    for (UUID id : employeIds) {
      List<PlanningTeletravail> plannings =
          planningTeletravailRepository.findByEmployeIdOrderByDateDebutDesc(id);
      List<Pointage> pointagesEmploye = pointagesParEmploye.getOrDefault(id, List.of());
      List<PeriodeCongeApprouve> conges = congesApprouves(id, debut, fin);
      for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
        if (jour.getDayOfWeek() == DayOfWeek.SUNDAY
            || jour.getDayOfWeek() == DayOfWeek.SATURDAY
            || joursFeries.contains(jour)) {
          continue;
        }
        String statut =
            ligneExportJour("", jour, plannings, pointagesEmploye, conges, pauseMidiA(jour)).get(3);
        if (!"Absence".equals(statut) && !statut.startsWith("Anomalie")) {
          joursCouverts++;
        }
      }
    }
    return 100.0 * joursCouverts / (joursOuvres * employeIds.size());
  }

  private List<AnomalieLigne> anomaliesEntre(LocalDate debut, LocalDate fin) {
    return jdbcTemplate.query(
        "select employe_id, type_anomalie, resolue from anomalies_pointage"
            + " where date_pointage between ? and ?",
        (rs, rowNum) ->
            new AnomalieLigne(
                (UUID) rs.getObject("employe_id"),
                TypeAnomaliePointage.valueOf(rs.getString("type_anomalie")),
                rs.getBoolean("resolue")),
        debut,
        fin);
  }

  private record AnomalieLigne(UUID employeId, TypeAnomaliePointage type, boolean resolue) {}

  /**
   * EF-ATT-04/EF-EXP-02 : jours fériés (module administratif, table {@code jours_feries}) exclus du
   * calcul de présence, au même titre que le week-end. Lecture SQL brute plutôt qu'injection du
   * service administratif (package-private, non exporté hors de {@code ma.hbdev.rh.administrative})
   * — même principe que {@link #congesApprouves}.
   */
  private Set<LocalDate> joursFeriesEntre(LocalDate debut, LocalDate fin) {
    return new HashSet<>(
        jdbcTemplate.queryForList(
            "select date_ferie from jours_feries where date_ferie between ? and ?",
            LocalDate.class,
            debut,
            fin));
  }

  /** Vrai si {@code date} est un jour férié déclaré (module administratif). */
  boolean estJourFerie(LocalDate date) {
    Integer compte =
        jdbcTemplate.queryForObject(
            "select count(*) from jours_feries where date_ferie = ?", Integer.class, date);
    return compte != null && compte > 0;
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

  // Package-private (au lieu de private) : réutilisé par AnomalieService#lister pour appliquer la
  // même restriction de périmètre Manager (EF-AUTH-03) que l'export ci-dessous.
  List<UUID> employesDansPerimetre(UUID employeId) {
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
      List<PeriodeCongeApprouve> conges,
      Duration pauseMidi) {
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
      duree = formatDuree(dureeDepuisPointages(pointagesJour, pauseMidi));
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
