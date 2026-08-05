package ma.hbdev.rh.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import ma.hbdev.rh.auth.LoginRequest;
import ma.hbdev.rh.auth.RoleUtilisateur;
import ma.hbdev.rh.auth.User;
import ma.hbdev.rh.auth.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AttendanceIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:14-alpine")
          .withDatabaseName("rh_test")
          .withUsername("rh_test")
          .withPassword("rh_test");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PointageService pointageService;
  @Autowired private AnomalieService anomalieService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String managerToken;
  private String deviceToken;

  @BeforeEach
  void authentifierAdmin() throws Exception {
    // Un seul test existait jusqu'ici (jamais de second passage par @BeforeEach dans la même
    // classe/conteneur) — le nettoyage partiel suffisait par accident. Avec plusieurs tests,
    // l'admin/département/employé du test précédent restent référencés (journal_audit, noms
    // uniques de département...) : nettoyage complet nécessaire, même pattern que
    // AdministrativeIntegrationTest/ConfigurationIntegrationTest.
    jdbcTemplate.execute(
        """
        truncate table notifications_mattermost, notifications_in_app, journal_audit,
          pointages, anomalies_pointage, qr_codes, kiosque_activations, mouvements_conges,
          demandes_administratives, employes, departements, sessions_utilisateur,
          horaires_reference, plannings_teletravail
        cascade
        """);
    // politique_anomalies (EF-ATT-11) est une donnée de référence seedée par V12, jamais recréée
    // par ce test — remise au défaut V12 à chaque test pour rester indépendant de l'ordre
    // d'exécution des méthodes (JUnit ne le garantit pas), même piège que politique_conges/T4.B2.
    // modifie_par référence utilisateurs(id) sans ON DELETE : nettoyer avant DELETE FROM
    // utilisateurs ci-dessous.
    jdbcTemplate.update("UPDATE politique_anomalies SET modifie_par = NULL");
    jdbcTemplate.update("UPDATE politique_anomalies SET seuil_anomalies = 3, periode_jours = 30");
    jdbcTemplate.execute("DELETE FROM utilisateurs");

    User admin = new User();
    admin.setEmail("admin@hbdev.ma");
    admin.setMotDePasseHash(passwordEncoder.encode("AdminPass@2025"));
    admin.setRole(RoleUtilisateur.admin);
    admin.setNom("System");
    admin.setPrenom("Admin");
    userRepository.save(admin);

    User manager = new User();
    manager.setEmail("manager@hbdev.ma");
    manager.setMotDePasseHash(passwordEncoder.encode("ManagerPass@2025"));
    manager.setRole(RoleUtilisateur.manager);
    manager.setNom("Alaoui");
    manager.setPrenom("Sara");
    userRepository.save(manager);

    String requete =
        objectMapper.writeValueAsString(new LoginRequest("admin@hbdev.ma", "AdminPass@2025"));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requete))
            .andExpect(status().isOk())
            .andReturn();
    adminToken =
        objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();

    String requeteManager =
        objectMapper.writeValueAsString(new LoginRequest("manager@hbdev.ma", "ManagerPass@2025"));
    MvcResult resultManager =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requeteManager))
            .andExpect(status().isOk())
            .andReturn();
    managerToken =
        objectMapper
            .readTree(resultManager.getResponse().getContentAsString())
            .at("/data/token")
            .asText();

    // NFR-UX-02 : /api/kiosque/scan exige un jeton d'appareil activé — un appareil de test
    // l'obtient
    // une fois ici pour que les tests existants (déjà focalisés sur le scan lui-même) n'aient pas à
    // rejouer le flux d'activation à chaque fois. Ce flux est testé pour lui-même séparément (cf.
    // gereLActivationKiosqueAvecVerrouillageEtRevocation).
    deviceToken = activerAppareilKiosqueDeTest();
  }

  private String activerAppareilKiosqueDeTest() throws Exception {
    String resGenerer =
        mockMvc
            .perform(
                post("/api/kiosque/activations").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = objectMapper.readTree(resGenerer).at("/data/code").asText();

    String resVerifier =
        mockMvc
            .perform(
                post("/api/kiosque/activation/verifier")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CodeActivationRequete(code))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resVerifier).at("/data/jetonAppareil").asText();
  }

  @Test
  void testQrCodeEtPointageKiosque() throws Exception {
    // 1. Créer un département
    String reqDept = "{\"nom\":\"RH Test\",\"managerId\":null}";
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    // 2. Créer un employé
    String reqEmp =
        """
        {
          "nom": "Test",
          "prenom": "Employe",
          "email": "test@hbdev.ma",
          "telephone": "0600000000",
          "poste": "Dev",
          "departementId": "%s",
          "dateEmbauche": "2025-01-01",
          "typeContrat": "CDI"
        }
        """
            .formatted(deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    // 3. Générer QR code
    String resQr =
        mockMvc
            .perform(
                post("/api/pointages/qr-code/generer/" + employeId)
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String valeurQr = objectMapper.readTree(resQr).at("/data/valeur").asText();

    // Scan kiosque entrée
    String scanReqEntree =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.entree));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.typeScan").value("entree"));

    // Double entrée -> refusée
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isConflict());

    // Scan kiosque sortie
    String scanReqSortie =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.sortie));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqSortie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.typeScan").value("sortie"));
  }

  @Test
  void giereLaPolitiqueDAnomalies() throws Exception {
    mockMvc
        .perform(get("/api/politique-anomalies").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seuilAnomalies").value(3))
        .andExpect(jsonPath("$.data.periodeJours").value(30));

    mockMvc
        .perform(
            put("/api/politique-anomalies")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seuilAnomalies\": 5, \"periodeJours\": 60}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/politique-anomalies")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seuilAnomalies\": 5, \"periodeJours\": 60}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seuilAnomalies").value(5))
        .andExpect(jsonPath("$.data.periodeJours").value(60))
        .andExpect(jsonPath("$.data.modifiePar").isNotEmpty());
  }

  @Test
  void declencheUneAlerteUneSeuleFoisAuFranchissementDuSeuil() throws Exception {
    mockMvc
        .perform(
            put("/api/politique-anomalies")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seuilAnomalies\": 2, \"periodeJours\": 30}"))
        .andExpect(status().isOk());

    UUID managerId =
        jdbcTemplate.queryForObject(
            "select id from utilisateurs where email = ?", UUID.class, "manager@hbdev.ma");

    String reqDept = "{\"nom\":\"RH Seuil Test\",\"managerId\":\"%s\"}".formatted(managerId);
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    String reqEmp =
        """
        {
          "nom": "Seuil",
          "prenom": "Employe",
          "email": "seuil@hbdev.ma",
          "telephone": "0600000001",
          "poste": "Dev",
          "departementId": "%s",
          "dateEmbauche": "2025-01-01",
          "typeContrat": "CDI"
        }
        """
            .formatted(deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    // La création de l'employé déclenche déjà sa propre notification au manager (EF-EMP-13) —
    // on repart d'un compteur à zéro pour isoler la mesure sur les seules alertes de seuil.
    jdbcTemplate.update("DELETE FROM notifications_in_app");

    LocalDate aujourdHui = LocalDate.now();

    // 1re anomalie : sous le seuil (2), pas d'alerte.
    pointageService.enregistrerAnomalieIdempotent(
        employeId, aujourdHui, TypeAnomaliePointage.retard, null, null);
    assertThat(compterNotificationsManager(managerId)).isZero();

    // 2e anomalie : seuil atteint, une alerte.
    pointageService.enregistrerAnomalieIdempotent(
        employeId, aujourdHui.minusDays(1), TypeAnomaliePointage.retard, null, null);
    assertThat(compterNotificationsManager(managerId)).isEqualTo(1);

    // 3e anomalie : au-delà du seuil, pas de nouvelle alerte (evite la spirale de notifications).
    pointageService.enregistrerAnomalieIdempotent(
        employeId, aujourdHui.minusDays(2), TypeAnomaliePointage.retard, null, null);
    assertThat(compterNotificationsManager(managerId)).isEqualTo(1);
  }

  // EF-EXP-02 / EF-ATT-10
  @Test
  void exporteLaFeuilleDePresenceAvecDistinctionTeletravailEtAbsence() throws Exception {
    UUID managerId =
        jdbcTemplate.queryForObject(
            "select id from utilisateurs where email = ?", UUID.class, "manager@hbdev.ma");
    String reqDept = "{\"nom\":\"RH Export Test\",\"managerId\":\"%s\"}".formatted(managerId);
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    String reqEmp =
        """
        {"nom":"Presence","prenom":"Employe","email":"presence.export@hbdev.ma",
         "telephone":"0600000002","poste":"Dev","departementId":"%s",
         "dateEmbauche":"2025-01-01","typeContrat":"CDI"}
        """
            .formatted(deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    String resQr =
        mockMvc
            .perform(
                post("/api/pointages/qr-code/generer/" + employeId)
                    .header("Authorization", "Bearer " + adminToken))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID qrCodeId = UUID.fromString(objectMapper.readTree(resQr).at("/data/id").asText());

    LocalDate lundi = LocalDate.now().plusDays(10);
    while (lundi.getDayOfWeek() != DayOfWeek.MONDAY) {
      lundi = lundi.plusDays(1);
    }
    LocalDate mercredi = lundi.plusDays(2);
    LocalDate jeudi = lundi.plusDays(3);
    LocalDate vendredi = lundi.plusDays(4);
    ZoneId zone = ZoneId.of("Africa/Casablanca");

    // Vendredi : jour férié déclaré (EF-ATT-04/EF-EXP-02), sans scan ni planning -> exclu de
    // l'export au même titre qu'un week-end, pas compté comme "Absence".
    jdbcTemplate.update(
        "insert into jours_feries(id, date_ferie, libelle) values (gen_random_uuid(), ?, ?)",
        vendredi,
        "Jour férié test");

    // Lundi : scan réel entrée/sortie -> "Présent". Mardi : couvert par un planning de
    // télétravail -> "Télétravail" malgré l'absence de scan. Mercredi : ni scan ni télétravail ->
    // "Absence". Jeudi : couvert par un congé approuvé -> "Congé" plutôt que "Absence" (EF-ATT-10,
    // ajouté le 2026-07-27 — un congé validé rendait "Absence" comme un vrai no-show). Insertion
    // directe en SQL (comme AuditIntegrationTest#insererEntree) car le seul chemin applicatif pour
    // un pointage est le scan kiosque, toujours horodaté à Instant.now() — impossible de fabriquer
    // un historique déterministe autrement ; même principe repris pour la demande de congé afin de
    // rester indépendant du solde de congés réel de l'employé de test.
    jdbcTemplate.update(
        "insert into pointages(employe_id, qr_code_id, type_scan, horodatage) values"
            + " (?, ?, cast('entree' as type_scan_pointage), ?)",
        employeId,
        qrCodeId,
        Timestamp.from(lundi.atTime(9, 0).atZone(zone).toInstant()));
    jdbcTemplate.update(
        "insert into pointages(employe_id, qr_code_id, type_scan, horodatage) values"
            + " (?, ?, cast('sortie' as type_scan_pointage), ?)",
        employeId,
        qrCodeId,
        Timestamp.from(lundi.atTime(17, 0).atZone(zone).toInstant()));

    mockMvc
        .perform(
            post("/api/employes/{employeId}/teletravail", employeId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dateDebut":"%s","dateFin":"%s","jours":["mardi"]}
                    """
                        .formatted(lundi, mercredi)))
        .andExpect(status().isCreated());

    jdbcTemplate.update(
        """
        insert into demandes_administratives(employe_id, type_demande, granularite, statut,
          date_debut, date_fin)
        values (?, cast('conge' as type_demande_administrative),
          cast('journee' as granularite_conge),
          cast('approuvee' as statut_demande_administrative), ?, ?)
        """,
        employeId,
        jeudi,
        jeudi);

    byte[] corps =
        mockMvc
            .perform(
                get("/api/pointages/export")
                    .header("Authorization", "Bearer " + adminToken)
                    .param("format", "xlsx")
                    .param("employeId", employeId.toString())
                    .param("debut", lundi.toString())
                    .param("fin", vendredi.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(corps))) {
      var feuille = classeur.getSheetAt(0);
      // En-tête + 4 jours (lundi/mardi/mercredi/jeudi) : vendredi est férié, donc exclu malgré
      // qu'il soit dans la plage demandée — pas de 5e ligne.
      assertThat(feuille.getLastRowNum()).isEqualTo(4);
      assertThat(feuille.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Présent");
      // 09h-17h = 8h, moins la pause midi d'1h déduite systématiquement (EF-ATT-03) = 7h00.
      assertThat(feuille.getRow(1).getCell(4).getStringCellValue()).isEqualTo("7h00");
      assertThat(feuille.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Télétravail");
      assertThat(feuille.getRow(3).getCell(3).getStringCellValue()).isEqualTo("Absence");
      assertThat(feuille.getRow(4).getCell(3).getStringCellValue()).isEqualTo("Congé");
    }
  }

  // EF-ATT-06 : correction manuelle d'un pointage par un Admin, avec traçabilité (NFR-SEC-03).
  @Test
  void corrigeManuellementUnPointageEtJournaliseLAudit() throws Exception {
    UUID employeId = creerEmployeAvecQrCode("Correction", "correction@hbdev.ma", "0600000003");

    // Un seul pointage réel disponible côté applicatif : le scan kiosque (toujours Instant.now()).
    // On corrige ensuite son horodatage, cas d'usage EF-ATT-06 (oubli de scan -> mauvaise heure
    // retenue par erreur, à rectifier).
    String valeurQr =
        jdbcTemplate.queryForObject(
            "select valeur from qr_codes where employe_id = ? and actif = true",
            String.class,
            employeId);
    String scanReq =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.entree));
    String resScan =
        mockMvc
            .perform(
                post("/api/kiosque/scan")
                    .header("X-Kiosque-Device-Token", deviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(scanReq))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String pointageId = objectMapper.readTree(resScan).at("/data/id").asText();

    Instant nouvelHorodatage =
        LocalDate.now().atTime(8, 45).atZone(ZoneId.of("Africa/Casablanca")).toInstant();
    String requeteCorrection =
        """
        {"nouvelHorodatage":"%s","motif":"Oubli de scan a l'arrivee, corrige sur justificatif"}
        """
            .formatted(nouvelHorodatage);

    // Manager -> interdit, réservé à l'Admin (EF-ATT-06).
    mockMvc
        .perform(
            post("/api/pointages/{id}/corriger", pointageId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCorrection))
        .andExpect(status().isForbidden());

    // Motif vide -> rejeté (traçabilité obligatoire).
    mockMvc
        .perform(
            post("/api/pointages/{id}/corriger", pointageId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nouvelHorodatage":"%s","motif":""}
                    """
                        .formatted(nouvelHorodatage)))
        .andExpect(status().isBadRequest());

    // Pointage inconnu -> 404.
    mockMvc
        .perform(
            post("/api/pointages/{id}/corriger", UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCorrection))
        .andExpect(status().isNotFound());

    // Admin -> corrige avec succès.
    mockMvc
        .perform(
            post("/api/pointages/{id}/corriger", pointageId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requeteCorrection))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.corrigeManuellement").value(true))
        .andExpect(
            jsonPath("$.data.motifCorrection")
                .value("Oubli de scan a l'arrivee, corrige sur justificatif"));

    // Traçabilité NFR-SEC-03 : un événement d'audit append-only, associé au bon pointage.
    Integer nombreEvenements =
        jdbcTemplate.queryForObject(
            "select count(*) from journal_audit where entite_id = ?::uuid and action = ?",
            Integer.class,
            pointageId,
            "pointage.corrige_manuellement");
    assertThat(nombreEvenements).isEqualTo(1);
  }

  private UUID creerEmployeAvecQrCode(String nom, String email, String telephone) throws Exception {
    String reqDept = "{\"nom\":\"RH %s\",\"managerId\":null}".formatted(nom);
    String resDept =
        mockMvc
            .perform(
                post("/api/departements")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqDept))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deptId = objectMapper.readTree(resDept).at("/data/id").asText();

    String reqEmp =
        """
        {
          "nom": "%s",
          "prenom": "Employe",
          "email": "%s",
          "telephone": "%s",
          "poste": "Dev",
          "departementId": "%s",
          "dateEmbauche": "2025-01-01",
          "typeContrat": "CDI"
        }
        """
            .formatted(nom, email, telephone, deptId);
    String resEmp =
        mockMvc
            .perform(
                post("/api/employes")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqEmp))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID employeId = UUID.fromString(objectMapper.readTree(resEmp).at("/data/id").asText());

    mockMvc
        .perform(
            post("/api/pointages/qr-code/generer/" + employeId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    return employeId;
  }

  private int compterNotificationsManager(UUID managerId) {
    Integer nombre =
        jdbcTemplate.queryForObject(
            "select count(*) from notifications_in_app where destinataire_id = ?",
            Integer.class,
            managerId);
    return nombre == null ? 0 : nombre;
  }

  // EF-ATT-04/05 : marquage résolu d'une anomalie, réservé à l'Admin.
  @Test
  void marqueUneAnomalieResolueEtLeRefuseAuManager() throws Exception {
    UUID employeId = creerEmployeAvecQrCode("Resolution", "resolution@hbdev.ma", "0600000004");

    pointageService.enregistrerAnomalieIdempotent(
        employeId, LocalDate.now(), TypeAnomaliePointage.retard, null, null);
    UUID anomalieId =
        jdbcTemplate.queryForObject(
            "select id from anomalies_pointage where employe_id = ?", UUID.class, employeId);

    // Manager -> interdit (EF-ATT-04, seul l'Admin résout).
    mockMvc
        .perform(
            post("/api/anomalies/{id}/resoudre", anomalieId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // Anomalie inconnue -> 404.
    mockMvc
        .perform(
            post("/api/anomalies/{id}/resoudre", UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());

    // Admin -> résout avec succès, visible dans le filtre resolue=true.
    mockMvc
        .perform(
            post("/api/anomalies/{id}/resoudre", anomalieId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolue").value(true));

    mockMvc
        .perform(
            get("/api/anomalies")
                .header("Authorization", "Bearer " + adminToken)
                .param("resolue", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(anomalieId.toString()));
  }

  // EF-ATT-08/09/10 : CRUD planning télétravail + court-circuit du moteur d'anomalies nocturne
  // (ai-instructions.md : "un jour de télétravail planifié n'est jamais une absence").
  @Test
  void gereLePlanningTeletravailEtCourtCircuiteLaDetectionDAnomalies() throws Exception {
    // Horaire de référence en vigueur : sans lui, detecterAnomaliesNocturnes() s'arrête avant
    // même de consulter le planning télétravail (cf. AnomalieService, log "ignorées").
    mockMvc
        .perform(
            post("/api/horaires-reference")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"heureDebutMatin":"08:30:00","heureFinMatin":"13:00:00",
                     "heureDebutApresMidi":"14:00:00","heureFinApresMidi":"17:00:00",
                     "toleranceMinutes":10,"dateEffet":"2020-01-01"}
                    """))
        .andExpect(status().isCreated());

    UUID employeTeletravail =
        creerEmployeAvecQrCode("Teletravail", "teletravail@hbdev.ma", "0600000005");
    UUID employeTemoin = creerEmployeAvecQrCode("Temoin", "temoin@hbdev.ma", "0600000006");

    // Planning couvrant tous les jours de la semaine (ouvert, sans dateFin) pour ne pas dépendre
    // du jour d'exécution réel du test.
    String reqPlanning =
        """
        {"dateDebut":"2020-01-01","dateFin":null,
         "jours":["lundi","mardi","mercredi","jeudi","vendredi","samedi","dimanche"]}
        """;
    String resPlanning =
        mockMvc
            .perform(
                post("/api/employes/{id}/teletravail", employeTeletravail)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reqPlanning))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String planningId = objectMapper.readTree(resPlanning).at("/data/id").asText();

    mockMvc
        .perform(
            get("/api/employes/{id}/teletravail", employeTeletravail)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(planningId));

    // Chaque employé "entre" un jour ouvré donné sans "sortir" (absence_checkout si rien ne
    // l'empêche) : seul le témoin (sans planning télétravail) doit finir par être signalé. Un
    // lundi fixe dans le passé plutôt que "hier" (LocalDate.now() - 1) : le moteur exclut
    // désormais explicitement les week-ends (EF-ATT-04), donc un test réellement exécuté un lundi
    // aurait "hier" tombant un dimanche et ne détecterait jamais rien.
    LocalDate hier = LocalDate.of(2024, 1, 8); // lundi
    insererPointageEntreeBackdated(employeTeletravail, hier);
    insererPointageEntreeBackdated(employeTemoin, hier);

    anomalieService.detecterAnomaliesPourJournee(hier);

    Integer anomaliesTeletravail =
        jdbcTemplate.queryForObject(
            "select count(*) from anomalies_pointage where employe_id = ? and date_pointage = ?",
            Integer.class,
            employeTeletravail,
            hier);
    assertThat(anomaliesTeletravail).isZero();

    Integer anomaliesTemoin =
        jdbcTemplate.queryForObject(
            "select count(*) from anomalies_pointage where employe_id = ? and date_pointage = ? "
                + "and type_anomalie = 'absence_checkout'",
            Integer.class,
            employeTemoin,
            hier);
    assertThat(anomaliesTemoin).isEqualTo(1);

    // Manager -> interdit (CRUD réservé à l'Admin, lecture seule ouverte au Manager).
    mockMvc
        .perform(
            delete("/api/employes/{id}/teletravail/{planningId}", employeTeletravail, planningId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/api/employes/{id}/teletravail/{planningId}", employeTeletravail, planningId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/employes/{id}/teletravail", employeTeletravail)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());

    // Planning déjà supprimé -> 404.
    mockMvc
        .perform(
            delete("/api/employes/{id}/teletravail/{planningId}", employeTeletravail, planningId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  // EF-ATT-04 : un jour férié déclaré n'est pas un jour ouvré — un employé sans scan ce jour-là ne
  // doit générer aucune anomalie, au même titre qu'un week-end.
  @Test
  void exclutLesJoursFeriesDeLaDetectionDAnomalies() throws Exception {
    mockMvc
        .perform(
            post("/api/horaires-reference")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"heureDebutMatin":"08:30:00","heureFinMatin":"13:00:00",
                     "heureDebutApresMidi":"14:00:00","heureFinApresMidi":"17:00:00",
                     "toleranceMinutes":10,"dateEffet":"2020-01-01"}
                    """))
        .andExpect(status().isCreated());

    UUID employe = creerEmployeAvecQrCode("Ferie", "ferie@hbdev.ma", "0600000007");

    LocalDate ferie = LocalDate.of(2024, 1, 9); // mardi
    jdbcTemplate.update(
        "insert into jours_feries(id, date_ferie, libelle) values (gen_random_uuid(), ?, ?)",
        ferie,
        "Jour férié test");

    // Entrée sans sortie : aurait généré une anomalie absence_checkout n'importe quel jour ouvré.
    insererPointageEntreeBackdated(employe, ferie);

    anomalieService.detecterAnomaliesPourJournee(ferie);

    Integer anomalies =
        jdbcTemplate.queryForObject(
            "select count(*) from anomalies_pointage where employe_id = ? and date_pointage = ?",
            Integer.class,
            employe,
            ferie);
    assertThat(anomalies).isZero();
  }

  // Tableau de bord Présence : taux de couverture sur 30 jours + répartition des anomalies.
  @Test
  void afficheLeTableauDeBordPresence() throws Exception {
    mockMvc
        .perform(
            post("/api/horaires-reference")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"heureDebutMatin":"08:30:00","heureFinMatin":"13:00:00",
                     "heureDebutApresMidi":"14:00:00","heureFinApresMidi":"17:00:00",
                     "toleranceMinutes":10,"dateEffet":"2020-01-01"}
                    """))
        .andExpect(status().isCreated());

    UUID employeId = creerEmployeAvecQrCode("Dashboard", "dashboard@hbdev.ma", "0600000008");

    // Jour ouvré récent garanti (dans la fenêtre glissante de 30 jours, jamais un week-end),
    // indépendant du jour d'exécution réel du test.
    LocalDate jourTest = LocalDate.now(ZoneId.of("Africa/Casablanca")).minusDays(1);
    while (jourTest.getDayOfWeek() == DayOfWeek.SATURDAY
        || jourTest.getDayOfWeek() == DayOfWeek.SUNDAY) {
      jourTest = jourTest.minusDays(1);
    }
    ZoneId zone = ZoneId.of("Africa/Casablanca");
    UUID qrCodeId =
        jdbcTemplate.queryForObject(
            "select id from qr_codes where employe_id = ? and actif = true", UUID.class, employeId);
    jdbcTemplate.update(
        "insert into pointages(employe_id, qr_code_id, type_scan, horodatage) values"
            + " (?, ?, cast('entree' as type_scan_pointage), ?)",
        employeId,
        qrCodeId,
        Timestamp.from(jourTest.atTime(8, 45).atZone(zone).toInstant()));
    jdbcTemplate.update(
        "insert into pointages(employe_id, qr_code_id, type_scan, horodatage) values"
            + " (?, ?, cast('sortie' as type_scan_pointage), ?)",
        employeId,
        qrCodeId,
        Timestamp.from(jourTest.atTime(17, 0).atZone(zone).toInstant()));

    pointageService.enregistrerAnomalieIdempotent(
        employeId, jourTest, TypeAnomaliePointage.retard, null, null);

    mockMvc
        .perform(get("/api/pointages/dashboard").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.joursOuvresPeriode").value(org.hamcrest.Matchers.greaterThan(0)))
        .andExpect(
            jsonPath("$.data.tauxPresence30Jours").value(org.hamcrest.Matchers.greaterThan(0.0)))
        .andExpect(
            jsonPath("$.data.repartitionParType[?(@.type == 'retard')].nombre")
                .value(
                    org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
  }

  // Régression : détection "retard"/"départ anticipé" en temps réel
  // (PointageService#detecterAnomalieImmediate, appelée depuis un vrai scan kiosque) ne
  // consultait pas le planning télétravail — seul AnomalieService#analyserJourPourEmploye (job
  // nocturne, couvert ci-dessus par gereLePlanningTeletravailEtCourtCircuiteLaDetectionDAnomalies)
  // le faisait. Un employé en télétravail qui scanne quand même (dépose un dossier au bureau,
  // scénario hybride courant chez HB) se faisait donc marquer en retard à tort.
  @Test
  void scanTardifEnTeletravailNeCreePasDAnomalieImmediateMaisUnTemoinSansPlanningOui()
      throws Exception {
    // heureDebutMatin=00:00 + tolérance 0 : n'importe quelle heure réelle d'exécution du test est
    // "après la limite d'arrivée", pour ne pas dépendre de l'heure du poste qui lance la suite.
    mockMvc
        .perform(
            post("/api/horaires-reference")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"heureDebutMatin":"00:00:00","heureFinMatin":"13:00:00",
                     "heureDebutApresMidi":"14:00:00","heureFinApresMidi":"23:59:00",
                     "toleranceMinutes":0,"dateEffet":"2020-01-01"}
                    """))
        .andExpect(status().isCreated());

    UUID employeTeletravail =
        creerEmployeAvecQrCode(
            "TeletravailImmediat", "teletravail.immediat@hbdev.ma", "0600000007");
    UUID employeTemoin =
        creerEmployeAvecQrCode("TemoinImmediat", "temoin.immediat@hbdev.ma", "0600000008");

    // Planning couvrant tous les jours (comme le test nocturne ci-dessus) pour ne pas dépendre du
    // jour d'exécution réel.
    mockMvc
        .perform(
            post("/api/employes/{id}/teletravail", employeTeletravail)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dateDebut":"2020-01-01","dateFin":null,
                     "jours":["lundi","mardi","mercredi","jeudi","vendredi","samedi","dimanche"]}
                    """))
        .andExpect(status().isCreated());

    scannerEntree(employeTeletravail);
    scannerEntree(employeTemoin);

    LocalDate aujourdHui = LocalDate.now(ZoneId.of("Africa/Casablanca"));
    Integer anomaliesTeletravail =
        jdbcTemplate.queryForObject(
            "select count(*) from anomalies_pointage where employe_id = ? and date_pointage = ?",
            Integer.class,
            employeTeletravail,
            aujourdHui);
    assertThat(anomaliesTeletravail).isZero();

    Integer anomaliesTemoin =
        jdbcTemplate.queryForObject(
            "select count(*) from anomalies_pointage where employe_id = ? and date_pointage = ? "
                + "and type_anomalie = 'retard'",
            Integer.class,
            employeTemoin,
            aujourdHui);
    assertThat(anomaliesTemoin).isEqualTo(1);
  }

  private void scannerEntree(UUID employeId) throws Exception {
    String valeurQr =
        jdbcTemplate.queryForObject(
            "select valeur from qr_codes where employe_id = ? and actif = true",
            String.class,
            employeId);
    String scanReq =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.entree));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReq))
        .andExpect(status().isOk());
  }

  // NFR-UX-02 : jeton d'activation par appareil — remplace le permitAll() inconditionnel du
  // kiosque.
  @Test
  void gereLActivationKiosqueAvecVerrouillageEtRevocation() throws Exception {
    UUID employeId = creerEmployeAvecQrCode("Kiosque", "kiosque@hbdev.ma", "0600000007");
    String valeurQr =
        jdbcTemplate.queryForObject(
            "select valeur from qr_codes where employe_id = ? and actif = true",
            String.class,
            employeId);
    String scanReq =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.entree));

    // Sans jeton d'appareil -> refusé, même avec un QR code valide (l'appareil de @BeforeEach
    // n'est pas utilisé ici : ce test vérifie le flux d'activation lui-même).
    mockMvc
        .perform(post("/api/kiosque/scan").contentType(MediaType.APPLICATION_JSON).content(scanReq))
        .andExpect(status().isUnauthorized());

    // Génération réservée à l'Admin (ou délégué actif) : le Manager n'a pas le droit.
    mockMvc
        .perform(post("/api/kiosque/activations").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/api/kiosque/activations").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isCreated());

    // 5 tentatives erronées -> le code le plus récent en attente se verrouille, même politique que
    // le verrouillage de compte (AuthService#handleFailedAttempt). La réponse du 5e essai porte
    // déjà la date de déverrouillage (décompte côté UI), pas seulement un message générique.
    for (int i = 0; i < 4; i++) {
      mockMvc
          .perform(
              post("/api/kiosque/activation/verifier")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(new CodeActivationRequete("XXXXXX"))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.data").doesNotExist());
    }
    mockMvc
        .perform(
            post("/api/kiosque/activation/verifier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CodeActivationRequete("XXXXXX"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.data.verrouilleJusquA").exists());
    Instant verrouilleJusquA =
        jdbcTemplate.queryForObject(
            "select verrouille_jusqu_a from kiosque_activations order by emis_le desc limit 1",
            Instant.class);
    assertThat(verrouilleJusquA).isAfter(Instant.now());

    // Un essai de plus pendant le verrouillage porte lui aussi la date de déverrouillage.
    mockMvc
        .perform(
            post("/api/kiosque/activation/verifier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CodeActivationRequete("XXXXXX"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.data.verrouilleJusquA").exists());

    // Un second code, indépendant du premier (verrouillé) -> activation réussie.
    String resCode =
        mockMvc
            .perform(
                post("/api/kiosque/activations").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = objectMapper.readTree(resCode).at("/data/code").asText();
    String activationId = objectMapper.readTree(resCode).at("/data/id").asText();

    String resVerifier =
        mockMvc
            .perform(
                post("/api/kiosque/activation/verifier")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CodeActivationRequete(code))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String jeton = objectMapper.readTree(resVerifier).at("/data/jetonAppareil").asText();

    // Ressaisir ce même code (déjà consommé) -> message distinct, pas "invalide" comme une vraie
    // faute de frappe, et surtout pas pénalisé (rien à verrouiller, ce n'est pas une devinette).
    mockMvc
        .perform(
            post("/api/kiosque/activation/verifier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CodeActivationRequete(code))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Ce code a déjà été utilisé."));

    mockMvc
        .perform(get("/api/kiosque/activation/statut").header("X-Kiosque-Device-Token", jeton))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actif").value(true));

    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReq))
        .andExpect(status().isOk());

    // Révocation réservée à l'Admin (ou délégué actif) -> l'appareil perd son accès.
    mockMvc
        .perform(
            post("/api/kiosque/activations/{id}/revoquer", activationId)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/kiosque/activations/{id}/revoquer", activationId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/kiosque/activation/statut").header("X-Kiosque-Device-Token", jeton))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actif").value(false));

    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .header("X-Kiosque-Device-Token", jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReq))
        .andExpect(status().isUnauthorized());

    // Même message pour un code révoqué que pour un code activé — "déjà utilisé" couvre les deux
    // états consommés, cf. KiosqueActivationService#verifierCode.
    mockMvc
        .perform(
            post("/api/kiosque/activation/verifier")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CodeActivationRequete(code))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Ce code a déjà été utilisé."));
  }

  // Les scans réels horodatent toujours Instant.now() (kiosque) : impossible de simuler "hier" par
  // ce chemin. Insertion directe, même principe que la donnée de démo attendance déjà validée en
  // conditions réelles pour ce projet.
  private void insererPointageEntreeBackdated(UUID employeId, LocalDate jour) {
    UUID qrCodeId =
        jdbcTemplate.queryForObject(
            "select id from qr_codes where employe_id = ? and actif = true", UUID.class, employeId);
    UUID horaireId =
        jdbcTemplate.queryForObject(
            "select id from horaires_reference order by date_effet desc limit 1", UUID.class);
    jdbcTemplate.update(
        "insert into pointages (id, employe_id, qr_code_id, type_scan, horodatage,"
            + " horaire_reference_id) values (gen_random_uuid(), ?, ?, 'entree', ?, ?)",
        employeId,
        qrCodeId,
        Timestamp.from(jour.atTime(8, 30).atZone(ZoneId.of("Africa/Casablanca")).toInstant()),
        horaireId);
  }
}
