package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
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

  private static final BigDecimal ACQUISITION_MENSUELLE = new BigDecimal("1.5");

  private final DemandeAdministrativeRepository demandeRepository;
  private final MouvementCongeAdmRepository mouvementRepository;
  private final JourFerieRepository jourFerieRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher evenements;

  AdministrativeService(
      DemandeAdministrativeRepository demandeRepository,
      MouvementCongeAdmRepository mouvementRepository,
      JourFerieRepository jourFerieRepository,
      JdbcTemplate jdbcTemplate,
      ApplicationEventPublisher evenements) {
    this.demandeRepository = demandeRepository;
    this.mouvementRepository = mouvementRepository;
    this.jourFerieRepository = jourFerieRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.evenements = evenements;
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
    if (CurrentUser.hasRole("MANAGER")) {
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
    verifierAdmin();
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
    verifierAdmin();
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
    verifierAdmin();
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
    verifierPerimetreManager(employe);
    return new SoldeCongeReponse(employeId, employe.nomComplet(), solde(employe));
  }

  @Transactional(readOnly = true)
  java.util.List<MouvementCongeReponse> mouvements(UUID employeId) {
    EmployeInfo employe = employe(employeId);
    verifierPerimetreManager(employe);
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
  }

  private BigDecimal solde(EmployeInfo employe) {
    BigDecimal acquis = acquis(employe);
    return acquis
        .add(mouvementRepository.sommeMouvements(employe.id()))
        .setScale(1, RoundingMode.HALF_UP);
  }

  private BigDecimal acquis(EmployeInfo employe) {
    if (employe.dateEmbauche() == null
        || employe.typeContrat() == null
        || employe.typeContrat().startsWith("STAGIAIRE")) {
      return BigDecimal.ZERO;
    }
    long totalMois =
        Math.max(
                0,
                ChronoUnit.MONTHS.between(
                    employe.dateEmbauche().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1)))
            + 1;
    return ACQUISITION_MENSUELLE.multiply(BigDecimal.valueOf(totalMois));
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
      if (jour.getDayOfWeek() != DayOfWeek.SUNDAY && !feries.contains(jour)) {
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

  private void verifierAdmin() {
    if (!CurrentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Action reservee admin");
    }
  }

  private UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }
}
