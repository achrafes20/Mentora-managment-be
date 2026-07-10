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
  private final ApplicationEventPublisher evenements;

  DepartementService(
      DepartementRepository departementRepository, ApplicationEventPublisher evenements) {
    this.departementRepository = departementRepository;
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
    evenements.publishEvent(new DepartementModifieEvent(departement.getId(), "creation"));
    return departement;
  }

  Departement modifier(UUID id, DepartementRequete requete) {
    Departement departement = trouver(id);
    verifierNomDisponible(requete.nom(), departement.getNom());
    departement.setNom(requete.nom());
    departement.setManagerId(requete.managerId());
    evenements.publishEvent(new DepartementModifieEvent(departement.getId(), "modification"));
    return departement;
  }

  void desactiver(UUID id) {
    // NOTE (décision actée avec Taha) : le blocage EF-EMP-10 (aucun employé actif rattaché)
    // est différé à T1.B2 — le module Employé (entité JPA sur `employes`) n'existe pas encore.
    Departement departement = trouver(id);
    departement.desactiver();
    evenements.publishEvent(new DepartementModifieEvent(departement.getId(), "desactivation"));
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
