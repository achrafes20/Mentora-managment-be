package ma.hbdev.rh.config;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.audit.AuditConsultationService;
import ma.hbdev.rh.shared.audit.JournalAuditReponse;
import ma.hbdev.rh.shared.event.ModuleAudit;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.export.FormatageExport;
import ma.hbdev.rh.shared.export.TableauExportService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * EF-CFG-05 : export du journal d'audit, en réutilisant les mêmes filtres que l'écran de
 * consultation. {@link JournalAuditReponse} ne porte que l'UUID de l'utilisateur (l'écran
 * `AuditPage.tsx` résout le nom côté client via la liste des comptes déjà chargée) — un export
 * PDF/Excel n'a pas cet allié côté client, donc la résolution nom se fait ici.
 */
@Service
class AuditExportService {

  private static final List<String> ENTETES =
      List.of("Horodatage", "Utilisateur", "Action", "Module", "En délégation");

  // Verbe court par action brute (JournalAuditReponse#action, chaîne libre choisie par chaque
  // EvenementMetier) — combiné à libelleEntite() pour former la cellule "Action" finale (ex.
  // "Suppression — Période de blocage congés"). Les 3 actions historiquement en
  // "prefixe.modifiee" (config.identite_entreprise.modifiee, politique_conges.modifiee,
  // politique_anomalies.modifiee) sont couvertes par le suffixe ".modifiee" plutôt que recensées
  // une par une : le préfixe fait déjà doublon avec entiteType.
  private static final Map<String, String> LIBELLES_ACTION =
      Map.ofEntries(
          Map.entry("creation", "Création"),
          Map.entry("approbation", "Approbation"),
          Map.entry("suppression", "Suppression"),
          Map.entry("ingestion", "Réception CV"),
          Map.entry("relance_analyse", "Relance analyse IA"),
          Map.entry("entretien_planifie", "Planification entretien"),
          Map.entry("statut_decision_auto", "Décision auto (IA)"),
          Map.entry("statut_embauche", "Statut -> Embauché"),
          Map.entry("statut_entretien", "Statut -> Entretien"),
          Map.entry("statut_preselectionne", "Statut -> Présélectionné"),
          Map.entry("statut_rejete", "Statut -> Rejeté"));

  // Mêmes libellés FR que MODULE_LABELS côté AuditPage.tsx — dupliqué volontairement : ce sont deux
  // couches distinctes (texte d'un fichier généré côté serveur vs libellé d'écran), rien à
  // partager via l'API generée.
  private static final Map<ModuleAudit, String> LIBELLES_MODULE =
      Map.of(
          ModuleAudit.authentification, "Authentification",
          ModuleAudit.employe, "Employé",
          ModuleAudit.presence, "Présence",
          ModuleAudit.recrutement, "Recrutement",
          ModuleAudit.demande_administrative, "Demande administrative",
          ModuleAudit.document, "Document",
          ModuleAudit.configuration, "Configuration",
          ModuleAudit.delegation, "Délégation",
          ModuleAudit.notification, "Notification");

  // entiteType (JournalAuditReponse) est une chaîne libre (chaque EvenementMetier choisit la
  // sienne, cf. *Event.java des modules propriétaires), pas un enum — recensées ici à la lecture du
  // code des 10 valeurs actuellement émises ; toute nouvelle valeur non recensée retombe sur
  // #libelleParDefaut plutôt que de planter.
  private static final Map<String, String> LIBELLES_ENTITE =
      Map.ofEntries(
          Map.entry("employe", "Employé"),
          Map.entry("departement", "Département"),
          Map.entry("demande_administrative", "Demande administrative"),
          Map.entry("periode_blocage_conges", "Période de blocage congés"),
          Map.entry("politique_conges", "Politique de congés"),
          Map.entry("politique_anomalies", "Politique d'anomalies"),
          Map.entry("identite_entreprise", "Identité entreprise"),
          Map.entry("import_lot", "Lot d'import"),
          Map.entry("candidature", "Candidature"),
          Map.entry("offre_emploi", "Offre d'emploi"));

  private final AuditConsultationService auditConsultationService;
  private final TableauExportService tableauExportService;
  private final JdbcTemplate jdbcTemplate;

  AuditExportService(
      AuditConsultationService auditConsultationService,
      TableauExportService tableauExportService,
      JdbcTemplate jdbcTemplate) {
    this.auditConsultationService = auditConsultationService;
    this.tableauExportService = tableauExportService;
    this.jdbcTemplate = jdbcTemplate;
  }

  byte[] exporter(
      ModuleAudit module,
      UUID utilisateurId,
      LocalDate debut,
      LocalDate fin,
      String recherche,
      FormatExport format) {
    List<JournalAuditReponse> entrees =
        auditConsultationService.rechercherPourExport(module, utilisateurId, debut, fin, recherche);
    Map<UUID, String> noms =
        nomsUtilisateurs(entrees.stream().map(JournalAuditReponse::utilisateurId).toList());
    List<List<String>> lignes = entrees.stream().map(e -> ligneExport(e, noms)).toList();
    return tableauExportService.generer(format, "Journal d'audit", ENTETES, lignes);
  }

  // Une requête par utilisateur distinct plutôt qu'un IN batché : même principe que employe() dans
  // AdministrativeService, le nombre de comptes Admin/Manager distincts sur une période reste
  // toujours faible (jamais un vrai N+1 sur le volume de lignes d'audit lui-même).
  private Map<UUID, String> nomsUtilisateurs(List<UUID> utilisateurIds) {
    Map<UUID, String> noms = new HashMap<>();
    for (UUID id : utilisateurIds.stream().distinct().toList()) {
      if (id == null || noms.containsKey(id)) {
        continue;
      }
      try {
        noms.put(
            id,
            jdbcTemplate.queryForObject(
                "select prenom || ' ' || nom from utilisateurs where id = ?", String.class, id));
      } catch (EmptyResultDataAccessException e) {
        noms.put(id, id.toString());
      }
    }
    return noms;
  }

  private static List<String> ligneExport(JournalAuditReponse entree, Map<UUID, String> noms) {
    String libelleModule =
        entree.module() == null
            ? ""
            : LIBELLES_MODULE.getOrDefault(entree.module(), texte(entree.module()));
    return List.of(
        FormatageExport.dateHeure(entree.horodatage()),
        noms.getOrDefault(entree.utilisateurId(), ""),
        libelleAction(entree.action(), entree.entiteType()),
        libelleModule,
        entree.enDelegation() ? "Oui" : "");
  }

  // Une seule cellule "Action" plutôt que deux colonnes séparées Action/Entité — celles-ci
  // avaient fini par se recouper (Module dit déjà "dans quel module", Entité ne faisait alors que
  // répéter "sur quoi" à côté d'un verbe déjà connu) ; toujours une seule ligne (verbe court +
  // libellé d'entité, jamais de texte libre/JSON).
  // "-" plutôt qu'un tiret cadratin : le rendu PDF (police Helvetica standard, cf.
  // PdfTableRenderer#versAsciiSimple) ne couvre que l'ASCII imprimable et remplace tout caractère
  // hors de cette plage par "?" — un tiret cadratin y devenait donc "?" au lieu du séparateur
  // voulu, repéré en relisant le PDF généré.
  private static String libelleAction(String action, String entiteType) {
    String verbe = libelleVerbe(action);
    String entite = libelleEntite(entiteType);
    return entite.isEmpty() ? verbe : verbe + " - " + entite;
  }

  private static String libelleVerbe(String action) {
    if (action == null) {
      return "";
    }
    if (action.endsWith(".modifiee")) {
      return "Modification";
    }
    return LIBELLES_ACTION.getOrDefault(action, libelleParDefaut(action));
  }

  private static String libelleEntite(String entiteType) {
    if (entiteType == null) {
      return "";
    }
    return LIBELLES_ENTITE.getOrDefault(entiteType, libelleParDefaut(entiteType));
  }

  // Valeur non recensée (nouvelle action ou nouvelle entiteType, oubli de mise à jour de la liste
  // ci-dessus) : "ma_nouvelle_action" -> "Ma nouvelle action" plutôt que de l'afficher brute ou de
  // planter.
  private static String libelleParDefaut(String valeur) {
    String avecEspaces = valeur.replace('_', ' ').replace('.', ' ');
    return avecEspaces.isEmpty()
        ? avecEspaces
        : Character.toUpperCase(avecEspaces.charAt(0)) + avecEspaces.substring(1);
  }

  private static String texte(Object valeur) {
    return valeur == null ? "" : valeur.toString();
  }
}
