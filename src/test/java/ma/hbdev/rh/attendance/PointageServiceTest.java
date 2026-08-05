package ma.hbdev.rh.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Tests unitaires isolés (Mockito, sans Spring ni base de données) pour compléter la couverture
 * d'intégration existante ({@link AttendanceIntegrationTest}) sur la logique pure de {@link
 * PointageService} : calcul de durée et escalade de seuil (EF-ATT-11).
 */
class PointageServiceTest {

  private PointageRepository pointageRepository;
  private QrCodeService qrCodeService;
  private HoraireReferenceService horaireReferenceService;
  private AnomaliePointageRepository anomalieRepository;
  private PolitiqueAnomaliesRepository politiqueAnomaliesRepository;
  private PlanningTeletravailRepository planningTeletravailRepository;
  private ApplicationEventPublisher evenements;
  private JdbcTemplate jdbcTemplate;
  private PointageService service;

  @BeforeEach
  void setUp() {
    pointageRepository = mock(PointageRepository.class);
    qrCodeService = mock(QrCodeService.class);
    horaireReferenceService = mock(HoraireReferenceService.class);
    anomalieRepository = mock(AnomaliePointageRepository.class);
    politiqueAnomaliesRepository = mock(PolitiqueAnomaliesRepository.class);
    planningTeletravailRepository = mock(PlanningTeletravailRepository.class);
    evenements = mock(ApplicationEventPublisher.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    service =
        new PointageService(
            pointageRepository,
            qrCodeService,
            horaireReferenceService,
            anomalieRepository,
            politiqueAnomaliesRepository,
            planningTeletravailRepository,
            evenements,
            jdbcTemplate,
            mock(ma.hbdev.rh.shared.export.TableauExportService.class));
  }

  // --- calculerDureeJour (EF-ATT-03) ---------------------------------------------------------

  @Test
  void calculerDureeJour_journeeCompleteAvecPauseMidi_deduitUneHeure() {
    UUID employeId = UUID.randomUUID();
    LocalDate jour = LocalDate.of(2026, 1, 15);
    Instant entree = jour.atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    Pointage pEntree =
        new Pointage(employeId, UUID.randomUUID(), TypeScanPointage.entree, entree, null);
    Pointage pSortie =
        new Pointage(
            employeId,
            UUID.randomUUID(),
            TypeScanPointage.sortie,
            entree.plus(Duration.ofHours(9)),
            null);
    when(pointageRepository.findByEmployeIdAndJour(any(), any(), any()))
        .thenReturn(List.of(pEntree, pSortie));

    Duration duree = service.calculerDureeJour(employeId, jour);

    assertThat(duree).isEqualTo(Duration.ofHours(8));
  }

  @Test
  void calculerDureeJour_segmentPlusCourtQueLaPause_neDevientPasNegatif() {
    UUID employeId = UUID.randomUUID();
    LocalDate jour = LocalDate.of(2026, 1, 15);
    Instant entree = jour.atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    Pointage pEntree =
        new Pointage(employeId, UUID.randomUUID(), TypeScanPointage.entree, entree, null);
    Pointage pSortie =
        new Pointage(
            employeId,
            UUID.randomUUID(),
            TypeScanPointage.sortie,
            entree.plus(Duration.ofMinutes(30)),
            null);
    when(pointageRepository.findByEmployeIdAndJour(any(), any(), any()))
        .thenReturn(List.of(pEntree, pSortie));

    Duration duree = service.calculerDureeJour(employeId, jour);

    assertThat(duree).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void calculerDureeJour_entreeSansSortie_rendZero() {
    UUID employeId = UUID.randomUUID();
    LocalDate jour = LocalDate.of(2026, 1, 15);
    Instant entree = jour.atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    Pointage pEntree =
        new Pointage(employeId, UUID.randomUUID(), TypeScanPointage.entree, entree, null);
    when(pointageRepository.findByEmployeIdAndJour(any(), any(), any()))
        .thenReturn(List.of(pEntree));

    Duration duree = service.calculerDureeJour(employeId, jour);

    assertThat(duree).isEqualTo(Duration.ZERO);
  }

  @Test
  void calculerDureeJour_pauseConfiguree_deduitLaDureeDeLHoraireEnVigueur() {
    UUID employeId = UUID.randomUUID();
    LocalDate jour = LocalDate.of(2026, 1, 15);
    Instant entree = jour.atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    Pointage pEntree =
        new Pointage(employeId, UUID.randomUUID(), TypeScanPointage.entree, entree, null);
    Pointage pSortie =
        new Pointage(
            employeId,
            UUID.randomUUID(),
            TypeScanPointage.sortie,
            entree.plus(Duration.ofHours(9)),
            null);
    when(pointageRepository.findByEmployeIdAndJour(any(), any(), any()))
        .thenReturn(List.of(pEntree, pSortie));
    // Pause de 30 min déduite de l'écart fin matin (13h00) / début après-midi (13h30) de
    // l'horaire en vigueur, au lieu des 60 min de repli par défaut.
    when(horaireReferenceService.trouverEnVigueurA(jour))
        .thenReturn(
            new HoraireReference(
                java.time.LocalTime.of(8, 30),
                java.time.LocalTime.of(13, 0),
                java.time.LocalTime.of(13, 30),
                java.time.LocalTime.of(17, 0),
                10,
                jour,
                null));

    Duration duree = service.calculerDureeJour(employeId, jour);

    assertThat(duree).isEqualTo(Duration.ofHours(8).plusMinutes(30));
  }

  @Test
  void calculerDureeJour_sansHoraireConfigure_seRabatSurUneHeureDePause() {
    UUID employeId = UUID.randomUUID();
    LocalDate jour = LocalDate.of(2026, 1, 15);
    Instant entree = jour.atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    Pointage pEntree =
        new Pointage(employeId, UUID.randomUUID(), TypeScanPointage.entree, entree, null);
    Pointage pSortie =
        new Pointage(
            employeId,
            UUID.randomUUID(),
            TypeScanPointage.sortie,
            entree.plus(Duration.ofHours(9)),
            null);
    when(pointageRepository.findByEmployeIdAndJour(any(), any(), any()))
        .thenReturn(List.of(pEntree, pSortie));
    when(horaireReferenceService.trouverEnVigueurA(jour)).thenReturn(null);

    Duration duree = service.calculerDureeJour(employeId, jour);

    assertThat(duree).isEqualTo(Duration.ofHours(8));
  }

  // --- estJourFerie (EF-ATT-04/EF-EXP-02) ----------------------------------------------------

  @Test
  void estJourFerie_dateDeclareeFeriee_rendVrai() {
    LocalDate date = LocalDate.of(2026, 5, 1);
    when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(date))).thenReturn(1);

    assertThat(service.estJourFerie(date)).isTrue();
  }

  @Test
  void estJourFerie_dateOrdinaire_rendFaux() {
    LocalDate date = LocalDate.of(2026, 5, 4);
    when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(date))).thenReturn(0);

    assertThat(service.estJourFerie(date)).isFalse();
  }

  // --- enregistrerAnomalieIdempotent / verifierSeuilAnomalies (EF-ATT-11) -------------------

  @Test
  void enregistrerAnomalieIdempotent_doublon_neReinsertePasEtNeReverifiePasLeSeuil() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    when(anomalieRepository.existsByEmployeIdAndDatePointageAndTypeAnomalie(
            employeId, date, TypeAnomaliePointage.retard))
        .thenReturn(true);

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(anomalieRepository, never()).save(any());
    verify(politiqueAnomaliesRepository, never()).findAll();
    verify(evenements, never()).publishEvent(any());
  }

  // --- notifierEmployeDeSaPropreAnomalie (EF-ATT-04) -------------------------------------------

  @Test
  void enregistrerAnomalieIdempotent_employeAvecCompte_notifieLEmployeDeSaPropreAnomalie() {
    UUID employeId = UUID.randomUUID();
    UUID utilisateurId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    mockEmployeInfo(employeId, "Jean Dupont", null, utilisateurId);
    when(anomalieRepository.save(any()))
        .thenReturn(new AnomaliePointage(employeId, date, TypeAnomaliePointage.retard, null, null));

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    ArgumentCaptor<AnomalieDetecteeEvent> captor =
        ArgumentCaptor.forClass(AnomalieDetecteeEvent.class);
    verify(evenements, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().utilisateurId()).isEqualTo(utilisateurId);
    assertThat(captor.getValue().type()).isEqualTo(TypeAnomaliePointage.retard);
  }

  @Test
  void enregistrerAnomalieIdempotent_employeSansCompte_neNotifiePersonne() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    // Employé sans compte applicatif (utilisateur_id nullable) : dégradation silencieuse, cf.
    // AnomalieDetecteeEvent#notification.
    mockEmployeInfo(employeId, "Jean Dupont", null, null);
    when(anomalieRepository.save(any()))
        .thenReturn(new AnomaliePointage(employeId, date, TypeAnomaliePointage.retard, null, null));

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    ArgumentCaptor<AnomalieDetecteeEvent> captor =
        ArgumentCaptor.forClass(AnomalieDetecteeEvent.class);
    verify(evenements, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().notification()).isNull();
  }

  @Test
  void enregistrerAnomalieIdempotent_sousLeSeuil_nAlertePas() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    politiqueAvecSeuil(3);
    episodeDejaAlerte(false);
    when(anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            any(), any()))
        .thenReturn(2L);

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(evenements, never()).publishEvent(any());
  }

  @Test
  void enregistrerAnomalieIdempotent_atteintExactementLeSeuil_alerteUneFoisEtMarqueLAnomalie() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    politiqueAvecSeuil(3);
    episodeDejaAlerte(false);
    mockEmployeInfo(employeId, "Jean Dupont", UUID.randomUUID());
    when(anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            any(), any()))
        .thenReturn(3L);
    AnomaliePointage anomalie =
        new AnomaliePointage(employeId, date, TypeAnomaliePointage.retard, null, null);
    when(anomalieRepository.save(any())).thenReturn(anomalie);

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(evenements, times(1)).publishEvent(any(SeuilAnomaliesDepasseEvent.class));
    assertThat(anomalie.isADeclencheAlerteSeuil()).isTrue();
  }

  /**
   * Reproduit précisément le bug corrigé : avant, la comparaison exacte ({@code ==}) — ou même une
   * comparaison {@code >=} basée sur un delta entre deux appels successifs — pouvait manquer
   * l'alerte si le compte dépassait directement le seuil sans jamais l'égaler pile (ex. seuil
   * abaissé par un Admin pendant qu'un employé a déjà des anomalies non résolues). Le drapeau
   * d'épisode ne dépend pas de la trajectoire du compte : tant qu'aucune anomalie non résolue de
   * l'épisode n'a déjà déclenché d'alerte, on notifie dès que le compte est au-dessus du seuil,
   * quelle que soit sa valeur exacte.
   */
  @Test
  void enregistrerAnomalieIdempotent_depasseLeSeuilSansLegalerPile_alerteQuandMeme() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    politiqueAvecSeuil(3);
    episodeDejaAlerte(false);
    mockEmployeInfo(employeId, "Jean Dupont", UUID.randomUUID());
    // Le compte "saute" directement à 5 sans jamais être passé par 3 (ex. seuil abaissé
    // entre-temps) : aucune vérification précédente n'a donc pu déjà notifier cet épisode.
    when(anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            any(), any()))
        .thenReturn(5L);
    when(anomalieRepository.save(any()))
        .thenReturn(new AnomaliePointage(employeId, date, TypeAnomaliePointage.retard, null, null));

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(evenements, times(1)).publishEvent(any(SeuilAnomaliesDepasseEvent.class));
  }

  @Test
  void enregistrerAnomalieIdempotent_episodeDejaAlerte_neReAlertePas() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    politiqueAvecSeuil(3);
    episodeDejaAlerte(true);
    when(anomalieRepository.countByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqual(
            any(), any()))
        .thenReturn(6L);

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(evenements, never()).publishEvent(any());
  }

  @Test
  void enregistrerAnomalieIdempotent_aucunePolitiqueConfiguree_nAlertePasEtNePlanteJamais() {
    UUID employeId = UUID.randomUUID();
    LocalDate date = LocalDate.now();
    when(politiqueAnomaliesRepository.findAll()).thenReturn(List.of());

    service.enregistrerAnomalieIdempotent(employeId, date, TypeAnomaliePointage.retard, null, null);

    verify(evenements, never()).publishEvent(any());
  }

  private void episodeDejaAlerte(boolean dejaAlerte) {
    when(anomalieRepository
            .existsByEmployeIdAndResolueFalseAndDatePointageGreaterThanEqualAndADeclencheAlerteSeuilTrue(
                any(), any()))
        .thenReturn(dejaAlerte);
  }

  private void politiqueAvecSeuil(int seuil) {
    PolitiqueAnomalies politique = new PolitiqueAnomalies();
    politique.setSeuilAnomalies(seuil);
    politique.setPeriodeJours(30);
    when(politiqueAnomaliesRepository.findAll()).thenReturn(List.of(politique));
  }

  private void mockEmployeInfo(UUID employeId, String nomComplet, UUID managerId) {
    mockEmployeInfo(employeId, nomComplet, managerId, null);
  }

  @SuppressWarnings("unchecked")
  private void mockEmployeInfo(
      UUID employeId, String nomComplet, UUID managerId, UUID utilisateurId) {
    String prenom = nomComplet.split(" ")[0];
    String nom = nomComplet.substring(prenom.length() + 1);
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(employeId)))
        .thenAnswer(
            invocation -> {
              RowMapper<?> mapper = invocation.getArgument(1);
              ResultSet rs = mock(ResultSet.class);
              when(rs.getString("prenom")).thenReturn(prenom);
              when(rs.getString("nom")).thenReturn(nom);
              when(rs.getObject("manager_id")).thenReturn(managerId);
              when(rs.getObject("utilisateur_id")).thenReturn(utilisateurId);
              return List.of(mapper.mapRow(rs, 0));
            });
  }
}
