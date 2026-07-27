package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AdministrativeService {

  // EF-ADM-11 : types de contrat valides (miroir léger du type Postgres type_contrat_employe,
  // sans dépendre du enum package-private employee.TypeContratEmploye — même principe que
  // employe(), déjà lu en String brut ici plutôt que via une dépendance croisée de module).
  private static final Set<String> TYPES_CONTRAT_CONNUS =
      Set.of("CDI", "CDD", "STAGIAIRE", "STAGIAIRE_REMUNERE");

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
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher evenements;
  private final DelegationService delegationService;
  private final TableauExportService tableauExportService;

  AdministrativeService(
      DemandeAdministrativeRepository demandeRepository,
      MouvementCongeAdmRepository mouvementRepository,
      JourFerieRepository jourFerieRepository,
      PeriodeBlocageCongesRepository periodeBlocageRepository,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher evenements,
      DelegationService delegationService,
      TableauExportService tableauExportService) {
    this.demandeRepository = demandeRepository;
    this.mouvementRepository = mouvementRepository;
    this.jourFerieRepository = jourFerieRepository;
    this.periodeBlocageRepository = periodeBlocageRepository;
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
    return demandeRepository
        .findAll(spec, pageable)
        .map(d -> DemandeAdministrativeReponse.depuis(d, employe(d.getEmployeId()), duree(d)));
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
  java.util.List<MouvementCongeReponse> mouvements(UUID employeId) {
    EmployeInfo employe = employe(employeId);
    verifierPerimetreManagerOuDelegue(employe);
    return mouvementRepository.findByEmployeIdOrderByDateMouvementDescCreeLeDesc(employeId).stream()
        .map(MouvementCongeReponse::depuis)
        .toList();
  }

  @Transactional(readOnly = true)
  java.util.List<JourFerieReponse> joursFeries() {
    return jourFerieRepository.findAllByOrderByDateFerieDesc().stream()
        .map(JourFerieReponse::depuis)
        .toList();
  }

  JourFerieReponse creerJourFerie(JourFerieRequete requete) {
    verifierAdmin();
    JourFerie jour =
        jourFerieRepository
            .findByDateFerie(requete.dateFerie())
            .map(
                existant -> {
                  existant.modifier(requete.libelle(), utilisateurCourant());
                  return existant;
                })
            .orElseGet(
                () -> new JourFerie(requete.dateFerie(), requete.libelle(), utilisateurCourant()));
    return JourFerieReponse.depuis(jourFerieRepository.save(jour));
  }

  void supprimerJourFerie(UUID id) {
    verifierAdmin();
    jourFerieRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  java.util.List<PeriodeBlocageCongesReponse> periodesBlocageConges() {
    return periodeBlocageRepository.findAllByOrderByDateDebutDesc().stream()
        .map(PeriodeBlocageCongesReponse::depuis)
        .toList();
  }

  PeriodeBlocageCongesReponse creerPeriodeBlocageConges(PeriodeBlocageCongesRequete requete) {
    verifierAdmin();
    if (requete.dateFin().isBefore(requete.dateDebut())) {
      throw new IllegalArgumentException("La date de fin doit etre apres la date de debut");
    }
    PeriodeBlocageConges periode =
        periodeBlocageRepository.save(
            new PeriodeBlocageConges(
                requete.dateDebut(), requete.dateFin(), requete.libelle(), utilisateurCourant()));
    evenements.publishEvent(new PeriodeBlocageCongesEvent(periode.getId(), "creation"));
    return PeriodeBlocageCongesReponse.depuis(periode);
  }

  void supprimerPeriodeBlocageConges(UUID id) {
    verifierAdmin();
    periodeBlocageRepository.deleteById(id);
    evenements.publishEvent(new PeriodeBlocageCongesEvent(id, "suppression"));
  }

  @Transactional(readOnly = true)
  List<PolitiqueCongeReponse> politiqueConges() {
    return jdbcTemplate.query(
        """
        select type_contrat::text, jours_par_mois, modifie_par, modifie_le
          from politique_conges
         order by type_contrat
        """,
        (rs, rowNum) ->
            new PolitiqueCongeReponse(
                rs.getString("type_contrat"),
                rs.getBigDecimal("jours_par_mois"),
                rs.getObject("modifie_par", UUID.class),
                // pgjdbc ne convertit pas timestamptz -> java.time.Instant directement via
                // getObject(col, Class) (seul OffsetDateTime/LocalDateTime le sont).
                rs.getObject("modifie_le", OffsetDateTime.class).toInstant()));
  }

  PolitiqueCongeReponse modifierPolitiqueConge(String typeContrat, BigDecimal joursParMois) {
    verifierAdmin();
    if (!TYPES_CONTRAT_CONNUS.contains(typeContrat)) {
      throw new IllegalArgumentException("Type de contrat inconnu : " + typeContrat);
    }
    jdbcTemplate.update(
        """
        update politique_conges
           set jours_par_mois = ?, modifie_par = ?, modifie_le = now()
         where type_contrat = cast(? as type_contrat_employe)
        """,
        joursParMois,
        utilisateurCourant(),
        typeContrat);
    evenements.publishEvent(new PolitiqueCongeModifieeEvent(typeContrat));
    return politiqueConges().stream()
        .filter(p -> p.typeContrat().equals(typeContrat))
        .findFirst()
        .orElseThrow();
  }

  private BigDecimal tauxAcquisitionMensuel(String typeContrat) {
    return jdbcTemplate.query(
        "select jours_par_mois from politique_conges where type_contrat = cast(? as type_contrat_employe)",
        rs -> rs.next() ? rs.getBigDecimal("jours_par_mois") : BigDecimal.ZERO,
        typeContrat);
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
    BigDecimal tauxMensuel = tauxAcquisitionMensuel(employe.typeContrat());
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
    if (type != TypeDemandeAdministrative.conge || debut == null || fin == null) {
      return BigDecimal.ZERO;
    }
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

  private EmployeInfo employe(UUID id) {
    return jdbcTemplate.queryForObject(
        """
        select id, nom, prenom, manager_id, date_embauche, type_contrat::text, statut::text
        from employes where id = ?
        """,
        (rs, rowNum) ->
            new EmployeInfo(
                rs.getObject("id", UUID.class),
                rs.getString("nom"),
                rs.getString("prenom"),
                rs.getObject("manager_id", UUID.class),
                rs.getObject("date_embauche", LocalDate.class),
                rs.getString("type_contrat"),
                rs.getString("statut")),
        id);
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

  private void verifierAdmin() {
    if (!CurrentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Action reservee admin");
    }
  }

  // EF-AUTH-11/12 : decision + actions adjacentes (approuver/rejeter/annuler) ouvertes au delegue
  // actif — jours feries/config restent verifierAdmin() strict, jamais delegables.
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
