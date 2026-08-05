package ma.hbdev.rh.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DepartementService {

  private final DepartementRepository departementRepository;
  private final EmployeRepository employeRepository;
  private final ApplicationEventPublisher evenements;

  DepartementService(
      DepartementRepository departementRepository,
      EmployeRepository employeRepository,
      ApplicationEventPublisher evenements) {
    this.departementRepository = departementRepository;
    this.employeRepository = employeRepository;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  List<Departement> lister() {
    return departementRepository.findAll();
  }

  Departement creer(DepartementRequete requete) {
    verifierNomDisponible(requete.nom(), null);
    Departement departement =
        departementRepository.save(new Departement(requete.nom(), requete.managerId()));
    evenements.publishEvent(
        new DepartementModifieEvent(departement.getId(), "creation", departement.getNom()));
    return departement;
  }

  Departement modifier(UUID id, DepartementRequete requete) {
    Departement departement = trouver(id);
    verifierNomDisponible(requete.nom(), departement.getNom());
    departement.setNom(requete.nom());
    departement.setManagerId(requete.managerId());
    evenements.publishEvent(
        new DepartementModifieEvent(departement.getId(), "modification", departement.getNom()));
    return departement;
  }

  void desactiver(UUID id) {
    Departement departement = trouver(id);
    // EF-EMP-10 : blocage si des employés actifs sont encore rattachés (guard différé de T1.B1,
    // le module Employé n'existait pas encore à l'époque).
    List<Employe> employesActifs =
        employeRepository.findByDepartementIdAndStatut(id, StatutActifInactif.actif);
    if (!employesActifs.isEmpty()) {
      throw new DepartementADesEmployesActifsException(employesActifs);
    }
    departement.desactiver();
    evenements.publishEvent(
        new DepartementModifieEvent(departement.getId(), "desactivation", departement.getNom()));
  }

  Departement activer(UUID id) {
    Departement departement = trouver(id);
    departement.activer();
    evenements.publishEvent(
        new DepartementModifieEvent(departement.getId(), "activation", departement.getNom()));
    return departement;
  }

  private void verifierNomDisponible(String nomDemande, String nomActuel) {
    boolean nomInchange = nomActuel != null && nomActuel.equalsIgnoreCase(nomDemande);
    if (!nomInchange && departementRepository.existsByNomIgnoreCase(nomDemande)) {
      throw new DepartementNomDejaUtiliseException(nomDemande);
    }
  }

  private Departement trouver(UUID id) {
    return departementRepository
        .findById(id)
        .orElseThrow(() -> new DepartementIntrouvableException(id));
  }
}
