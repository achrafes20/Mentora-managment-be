package ma.hbdev.rh.attendance;

import static org.assertj.core.api.Assertions.assertThat;
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
  @Autowired private JdbcTemplate jdbcTemplate;

  private String adminToken;
  private String managerToken;

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
          pointages, anomalies_pointage, qr_codes, mouvements_conges, demandes_administratives,
          employes, departements, sessions_utilisateur
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.typeScan").value("entree"));

    // Double entrée -> refusée
    mockMvc
        .perform(
            post("/api/kiosque/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scanReqEntree))
        .andExpect(status().isConflict());

    // Scan kiosque sortie
    String scanReqSortie =
        objectMapper.writeValueAsString(new ScanRequete(valeurQr, TypeScanPointage.sortie));
    mockMvc
        .perform(
            post("/api/kiosque/scan")
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
    ZoneId zone = ZoneId.of("Africa/Casablanca");

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
                    .param("fin", jeudi.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(corps))) {
      var feuille = classeur.getSheetAt(0);
      // En-tête + 4 jours (lundi/mardi/mercredi/jeudi, aucun dimanche dans la plage).
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
                post("/api/kiosque/scan").contentType(MediaType.APPLICATION_JSON).content(scanReq))
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
}
