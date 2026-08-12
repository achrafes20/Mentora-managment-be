package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.hbdev.rh.auth.DelegationService;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.export.FormatageExport;
import ma.hbdev.rh.shared.export.TableauExportService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie des demandes administratives (congés, bons de sortie, documents libres) et calcul de
 * solde de congés. Les CRUD annexes (jours fériés, périodes de blocage, politique de congés) vivent
 * dans leurs propres services — {@link JourFerieService}, {@link PeriodeBlocageCongesService},
 * {@link PolitiqueCongeService} — extraits pour ne pas mélanger 5 responsabilités dans une seule
 * classe ; les repositories correspondants restent injectés ici directement pour les simples
 * lectures utilisées par la validation (pas d'aller-retour inter-services pour une requête d'une
 * ligne).
 */
@Service
@Transactional
class AdministrativeService {

  // EF-ADM-14 : les congés spéciaux ont une vraie plage de dates (durée réelle en jours ouvrés,
  // affichée notamment dans l'export) même s'ils ne débitent jamais le solde (cf. duree() et le
  // garde-fou sur TypeDemandeAdministrative ci-dessous, à ne pas confondre : "hors solde" et
  // "durée nulle" sont deux choses différentes). bon_sortie/document_libre/autre restent à 0, ils
  // ne représentent pas une absence en jours.
  private static final EnumSet<TypeDemandeAdministrative> TYPES_AVEC_DUREE_EN_JOURS =
      EnumSet.of(
          TypeDemandeAdministrative.conge,
          TypeDemandeAdministrative.conge_mariage,
          TypeDemandeAdministrative.conge_naissance,
          TypeDemandeAdministrative.conge_deces,
          TypeDemandeAdministrative.conge_maladie);

  private static final List<String> ENTETES_EXPORT_DEMANDES =
      List.of(
          "Employé",
          "Type",
          "Statut",
          "Date début",
          "Date fin",
          "Durée (jours)",
          "Motif",
          "Créé le",
          "Décision le",
          "Décidé par");

  private final DemandeAdministrativeRepository demandeRepository;
  private final MouvementCongeAdmRepository mouvementRepository;
  private final JourFerieRepository jourFerieRepository;
  private final PeriodeBlocageCongesRepository periodeBlocageRepository;
  private final PolitiqueCongeService politiqueCongeService;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher evenements;
  private final DelegationService delegationService;
  private final TableauExportService tableauExportService;

  AdministrativeService(
      DemandeAdministrativeRepository demandeRepository,
      MouvementCongeAdmRepository mouvementRepository,
      JourFerieRepository jourFerieRepository,
      PeriodeBlocageCongesRepository periodeBlocageRepository,
      PolitiqueCongeService politiqueCongeService,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher evenements,
      DelegationService delegationService,
      TableauExportService tableauExportService) {
    this.demandeRepository = demandeRepository;
    this.mouvementRepository = mouvementRepository;
    this.jourFerieRepository = jourFerieRepository;
    this.periodeBlocageRepository = periodeBlocageRepository;
    this.politiqueCongeService = politiqueCongeService;
    this.jdbcTemplate = jdbcTemplate;
    this.evenements = evenements;
    this.delegationService = delegationService;
    this.tableauExportService = tableauExportService;
  }

  @Transactional(readOnly = true)
  Page<DemandeAdministrativeReponse> lister(
      UUID employeId,
      TypeDemandeAdministrative type,
      StatutDemandeAdministrative statut,
      LocalDate debut,
      LocalDate fin,
      Pageable pageable) {
    Specification<DemandeAdministrative> spec = (root, query, cb) -> cb.conjunction();
    if (employeId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("employeId"), employeId));
    }
    if (type != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("typeDemande"), type));
    }
    if (statut != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("statut"), statut));
    }
    if (debut != null) {
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateDebut"), debut));
    }
    if (fin != null) {
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dateFin"), fin));
    }
    // EF-AUTH-11/12 : un délégué actif voit tout le périmètre (comme l'Admin) pour pouvoir
    // décider — sinon un délégué approuvant hors de sa propre équipe ne verrait jamais la
    // demande apparaître dans sa liste (bug E2E repéré le 2026-07-23, même défaut que
    // CandidatureService.lister()).
    if (CurrentUser.hasRole("MANAGER") && !delegationService.estDelegueActif()) {
      UUID managerId = utilisateurCourant();
      List<UUID> employesManager =
          jdbcTemplate.queryForList(
              "select id from employes where manager_id = ?", UUID.class, managerId);
      spec =
          spec.and(
              (root, query, cb) ->
                  employesManager.isEmpty()
                      ? cb.disjunction()
                      : root.get("employeId").in(employesManager));
    }
    Page<DemandeAdministrative> page = demandeRepository.findAll(spec, pageable);
    // Corrige un N+1 : une requête employes par ligne (jusqu'à `pageable.getPageSize()` requêtes
    // individuelles) devient une seule requête groupée, quelle que soit la taille de la page.
    Map<UUID, EmployeInfo> employesParId =
        employesParIds(
            page.getContent().stream().map(DemandeAdministrative::getEmployeId).toList());
    return page.map(
        d -> DemandeAdministrativeReponse.depuis(d, employesParId.get(d.getEmployeId()), duree(d)));
  }

  DemandeAdministrativeReponse creer(DemandeAdministrativeRequete requete) {
    EmployeInfo employe = employe(requete.employeId());
    verifierPerimetreManager(employe);
    valider(requete, employe);
    DemandeAdministrative demande =
        demandeRepository.save(new DemandeAdministrative(requete, utilisateurCourant()));
    evenements.publishEvent(
        new DemandeAdministrativeEvent(demande.getId(), "creation", null, employe.nomComplet()));
    return DemandeAdministrativeReponse.depuis(demande, employe, duree(demande));
  }

  DemandeAdministrativeReponse approuver(UUID id) {
    DemandeAdministrative demande = trouver(id);
    verifierAdminOuDelegue();
    if (demande.getStatut() != StatutDemandeAdministrative.en_attente) {
      throw new IllegalArgumentException("Seule une demande en attente peut etre approuvee");
    }
    EmployeInfo employe = employe(demande.getEmployeId());
    if (demande.getTypeDemande() == TypeDemandeAdministrative.conge) {
      BigDecimal duree = duree(demande);
      if (solde(employe).compareTo(duree) < 0) {
        throw new IllegalArgumentException("Solde de conges insuffisant");
      }
      mouvementRepository.save(
          new MouvementCongeAdm(
              employe.id(),
              demande.getId(),
              TypeMouvementCongeAdm.consommation,
              duree.negate(),
              "Consommation demande " + demande.getId(),
              utilisateurCourant()));
    }
    demande.approuver(utilisateurCourant());
    evenements.publishEvent(
        new DemandeAdministrativeEvent(
            demande.getId(), "approbation", employe.managerId(), employe.nomComplet()));
    return DemandeAdministrativeReponse.depuis(demande, employe, duree(demande));
  }

  DemandeAdministrativeReponse rejeter(UUID id) {
    DemandeAdministrative demande = trouver(id);
    verifierAdminOuDelegue();
    if (demande.getStatut() != StatutDemandeAdministrative.en_attente) {
      throw new IllegalArgumentException("Seule une demande en attente peut etre rejetee");
    }
    EmployeInfo employe = employe(demande.getEmployeId());
    demande.rejeter(utilisateurCourant());
    evenements.publishEvent(
        new DemandeAdministrativeEvent(
            demande.getId(), "rejet", employe.managerId(), employe.nomComplet()));
    return DemandeAdministrativeReponse.depuis(demande, employe, duree(demande));
  }

  DemandeAdministrativeReponse annuler(UUID id) {
    DemandeAdministrative demande = trouver(id);
    verifierAdminOuDelegue();
    if (demande.getStatut() != StatutDemandeAdministrative.approuvee) {
      throw new IllegalArgumentException("Seule une demande approuvee peut etre annulee");
    }
    EmployeInfo employe = employe(demande.getEmployeId());
    if (demande.getTypeDemande() == TypeDemandeAdministrative.conge
        && !mouvementRepository.existsByDemandeIdAndTypeMouvement(
            demande.getId(), TypeMouvementCongeAdm.recredit)) {
      mouvementRepository.save(
          new MouvementCongeAdm(
              employe.id(),
              demande.getId(),
              TypeMouvementCongeAdm.recredit,
              duree(demande),
              "Recredit annulation demande " + demande.getId(),
              utilisateurCourant()));
    }
    demande.annuler(utilisateurCourant());
    evenements.publishEvent(
        new DemandeAdministrativeEvent(demande.getId(), "annulation", null, employe.nomComplet()));
    return DemandeAdministrativeReponse.depuis(demande, employe, duree(demande));
  }

  @Transactional(readOnly = true)
  SoldeCongeReponse solde(UUID employeId) {
    EmployeInfo employe = employe(employeId);
    verifierPerimetreManagerOuDelegue(employe);
    return new SoldeCongeReponse(employeId, employe.nomComplet(), solde(employe));
  }

  @Transactional(readOnly = true)
  List<MouvementCongeReponse> mouvements(UUID employeId) {
    EmployeInfo employe = employe(employeId);
    verifierPerimetreManagerOuDelegue(employe);
    return mouvementRepository.findByEmployeIdOrderByDateMouvementDescCreeLeDesc(employeId).stream()
        .map(MouvementCongeReponse::depuis)
        .toList();
  }

  private void valider(DemandeAdministrativeRequete requete, EmployeInfo employe) {
    if (!"actif".equals(employe.statut())) {
      throw new IllegalArgumentException("L'employe doit etre actif");
    }
    switch (requete.typeDemande()) {
      case conge -> validerConge(requete, employe);
      case bon_sortie -> {
        if (requete.dateDebut() == null
            || requete.heureDepart() == null
            || requete.heureRetourPrevue() == null
            || !requete.heureRetourPrevue().isAfter(requete.heureDepart())) {
          throw new IllegalArgumentException(
              "Le bon de sortie exige une date et un creneau valide");
        }
        if (demandeRepository
            .existsByEmployeIdAndTypeDemandeAndStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                employe.id(),
                TypeDemandeAdministrative.conge,
                StatutDemandeAdministrative.approuvee,
                requete.dateDebut(),
                requete.dateDebut())) {
          throw new IllegalArgumentException("Un conge approuve existe deja sur cette date");
        }
      }
      case document_libre, autre -> {
        if (requete.motif() == null || requete.motif().isBlank()) {
          throw new IllegalArgumentException("Le motif est obligatoire");
        }
      }
      case conge_mariage, conge_naissance, conge_deces, conge_maladie ->
          validerCongeSpecial(requete);
    }
  }

  // EF-ADM-14 : congés légaux (mariage/naissance/décès/maladie) — juste une période valide,
  // volontairement aucun contrôle de solde ni de période de blocage (contrairement à
  // validerConge()) : ce sont des droits légaux distincts du congé payé, jamais décomptés du
  // quota. La maladie exige en plus un justificatif déjà téléversé (EF-ADM-14).
  private void validerCongeSpecial(DemandeAdministrativeRequete requete) {
    if (requete.dateDebut() == null) {
      throw new IllegalArgumentException("Une date de debut est requise");
    }
    LocalDate fin = requete.dateFin() == null ? requete.dateDebut() : requete.dateFin();
    if (fin.isBefore(requete.dateDebut())) {
      throw new IllegalArgumentException("La date de fin doit etre apres la date de debut");
    }
    if (requete.typeDemande() == TypeDemandeAdministrative.conge_maladie
        && requete.fichierDocumentLibreId() == null) {
      throw new IllegalArgumentException("Un justificatif est requis pour un conge maladie");
    }
  }

  private void validerConge(DemandeAdministrativeRequete requete, EmployeInfo employe) {
    if (requete.granularite() == null || requete.dateDebut() == null) {
      throw new IllegalArgumentException("Le conge exige une date et une granularite");
    }
    LocalDate fin = requete.dateFin() == null ? requete.dateDebut() : requete.dateFin();
    if (fin.isBefore(requete.dateDebut())) {
      throw new IllegalArgumentException("La date de fin doit etre apres la date de debut");
    }
    BigDecimal duree =
        duree(requete.typeDemande(), requete.granularite(), requete.dateDebut(), fin);
    if (duree.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("La duree du conge doit etre positive");
    }
    if (solde(employe).compareTo(duree) < 0) {
      throw new IllegalArgumentException("Solde de conges insuffisant");
    }
    if (demandeRepository
        .existsByEmployeIdAndTypeDemandeAndStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
            employe.id(),
            TypeDemandeAdministrative.conge,
            StatutDemandeAdministrative.approuvee,
            fin,
            requete.dateDebut())) {
      throw new IllegalArgumentException("Un conge approuve chevauche cette periode");
    }
    // EF-ADM-12 : une demande déjà approuvée avant la création d'une période de blocage n'est
    // jamais remise en cause (ce contrôle ne s'applique qu'à la création, pas à la lecture/decision
    // d'une demande existante).
    if (periodeBlocageRepository.chevaucheUnePeriodeBloquee(requete.dateDebut(), fin)) {
      throw new IllegalArgumentException(
          "Cette periode chevauche une periode de blocage des demandes de conge");
    }
  }

  private BigDecimal solde(EmployeInfo employe) {
    BigDecimal acquis = acquis(employe);
    return acquis
        .add(mouvementRepository.sommeMouvements(employe.id()))
        .setScale(1, RoundingMode.HALF_UP);
  }

  private BigDecimal acquis(EmployeInfo employe) {
    if (employe.dateEmbauche() == null || employe.typeContrat() == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal tauxMensuel = politiqueCongeService.tauxAcquisitionMensuel(employe.typeContrat());
    if (tauxMensuel.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    long totalMois =
        Math.max(
                0,
                ChronoUnit.MONTHS.between(
                    employe.dateEmbauche().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
            + 1;
    return tauxMensuel.multiply(BigDecimal.valueOf(totalMois));
  }

  private BigDecimal duree(DemandeAdministrative demande) {
    return duree(
        demande.getTypeDemande(),
        demande.getGranularite(),
        demande.getDateDebut(),
        demande.getDateFin() == null ? demande.getDateDebut() : demande.getDateFin());
  }

  private BigDecimal duree(
      TypeDemandeAdministrative type,
      GranulariteConge granularite,
      LocalDate debut,
      LocalDate fin) {
    if (!TYPES_AVEC_DUREE_EN_JOURS.contains(type) || debut == null || fin == null) {
      return BigDecimal.ZERO;
    }
    // La granularité demi-journée n'existe que pour "conge" (congés spéciaux non concernés, cf.
    // EF-ADM-14 — leur granularite reste toujours null en base).
    if (granularite == GranulariteConge.demi_matin
        || granularite == GranulariteConge.demi_apres_midi) {
      return new BigDecimal("0.5");
    }
    var feries = new HashSet<LocalDate>();
    jourFerieRepository.findByDateFerieBetweenOrderByDateFerie(debut, fin).stream()
        .map(JourFerie::getDateFerie)
        .forEach(feries::add);
    BigDecimal total = BigDecimal.ZERO;
    for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
      DayOfWeek jourSemaine = jour.getDayOfWeek();
      boolean weekEnd = jourSemaine == DayOfWeek.SATURDAY || jourSemaine == DayOfWeek.SUNDAY;
      if (!weekEnd && !feries.contains(jour)) {
        total = total.add(BigDecimal.ONE);
      }
    }
    return total;
  }

  private DemandeAdministrative trouver(UUID id) {
    return demandeRepository
        .findById(id)
        .orElseThrow(() -> new DemandeAdministrativeIntrouvableException(id));
  }

  private static final RowMapper<EmployeInfo> MAPPEUR_EMPLOYE_INFO =
      (rs, rowNum) ->
          new EmployeInfo(
              rs.getObject("id", UUID.class),
              rs.getString("nom"),
              rs.getString("prenom"),
              rs.getObject("manager_id", UUID.class),
              rs.getObject("date_embauche", LocalDate.class),
              rs.getString("type_contrat"),
              rs.getString("statut"));

  private EmployeInfo employe(UUID id) {
    return jdbcTemplate.queryForObject(
        """
        select id, nom, prenom, manager_id, date_embauche, type_contrat::text, statut::text
        from employes where id = ?
        """,
        MAPPEUR_EMPLOYE_INFO,
        id);
  }

  // Version groupée de employe(UUID) — évite un N+1 sur lister()/exporter() : une seule requête
  // pour toute une page au lieu d'une par ligne.
  private Map<UUID, EmployeInfo> employesParIds(List<UUID> ids) {
    List<UUID> distincts = ids.stream().distinct().toList();
    if (distincts.isEmpty()) {
      return Map.of();
    }
    // IN dynamique plutôt que = any(?) : évite de dépendre de java.sql.Array/le support driver
    // pour convertir un tableau Java en tableau Postgres — juste des placeholders JDBC standards.
    String placeholders = distincts.stream().map(id -> "?").collect(Collectors.joining(","));
    List<EmployeInfo> employes =
        jdbcTemplate.query(
            "select id, nom, prenom, manager_id, date_embauche, type_contrat::text, statut::text "
                + "from employes where id in ("
                + placeholders
                + ")",
            MAPPEUR_EMPLOYE_INFO,
            distincts.toArray());
    return employes.stream().collect(Collectors.toMap(EmployeInfo::id, e -> e));
  }

  private void verifierPerimetreManager(EmployeInfo employe) {
    if (CurrentUser.hasRole("MANAGER") && !utilisateurCourant().equals(employe.managerId())) {
      throw new AccessDeniedException("Employe hors perimetre manager");
    }
  }

  // EF-AUTH-11/12 : variante utilisée pour solde()/mouvements(), consultées pour instruire une
  // décision d'approbation — un délégué doit pouvoir les lire hors de sa propre équipe. Jamais
  // utilisée par creer() : créer une demande pour un employé qu'on ne gère pas n'est pas un
  // droit d'approbation délégable, seulement une commodité Admin.
  private void verifierPerimetreManagerOuDelegue(EmployeInfo employe) {
    if (delegationService.estDelegueActif()) {
      return;
    }
    verifierPerimetreManager(employe);
  }

  // EF-AUTH-11/12 : decision + actions adjacentes (approuver/rejeter/annuler) ouvertes au delegue
  // actif — jours feries/config restent verifierAdmin() strict, jamais delegables (cf. les
  // services dédiés).
  private void verifierAdminOuDelegue() {
    if (!CurrentUser.hasRole("ADMIN") && !delegationService.estDelegueActif()) {
      throw new AccessDeniedException("Action reservee admin (ou delegue actif)");
    }
  }

  private UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }

  // EF-EXP-03 : mêmes filtres/périmètre (Manager + délégué actif) que lister(), pas de logique
  // dupliquée.
  @Transactional(readOnly = true)
  byte[] exporter(
      UUID employeId,
      TypeDemandeAdministrative type,
      StatutDemandeAdministrative statut,
      LocalDate debut,
      LocalDate fin,
      FormatExport format) {
    List<DemandeAdministrativeReponse> demandes =
        lister(employeId, type, statut, debut, fin, Pageable.unpaged()).getContent();
    Map<UUID, String> noms =
        nomsUtilisateurs(
            demandes.stream().map(DemandeAdministrativeReponse::approuveRejetePar).toList());
    List<List<String>> lignes = demandes.stream().map(d -> ligneExport(d, noms)).toList();
    return tableauExportService.generer(
        format, "Demandes administratives", ENTETES_EXPORT_DEMANDES, lignes);
  }

  // Même principe que AuditExportService#nomsUtilisateurs : une requête par utilisateur distinct
  // (toujours peu nombreux — Admin/délégués), pour afficher "Décidé par" en nom plutôt qu'en UUID.
  private Map<UUID, String> nomsUtilisateurs(List<UUID> utilisateurIds) {
    Map<UUID, String> noms = new HashMap<>();
    for (UUID id : utilisateurIds.stream().distinct().toList()) {
      if (id == null) {
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

  private static List<String> ligneExport(
      DemandeAdministrativeReponse demande, Map<UUID, String> noms) {
    return List.of(
        texte(demande.employeNomComplet()),
        texte(demande.typeDemande()),
        texte(demande.statut()),
        texte(demande.dateDebut()),
        texte(demande.dateFin()),
        texte(demande.dureeJours()),
        texte(demande.motif()),
        FormatageExport.dateHeure(demande.creeLe()),
        FormatageExport.dateHeure(demande.dateDecision()),
        noms.getOrDefault(demande.approuveRejetePar(), ""));
  }

  private static String texte(Object valeur) {
    return valeur == null ? "" : valeur.toString();
  }
}
